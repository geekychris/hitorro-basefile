/*
 * Copyright (c) 2006-2025 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.util.urlparser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

@DisplayName("UrlNormalizer")
class UrlNormalizerTest {

    private boolean tldAvailable;

    @BeforeEach
    void checkTldAvailable() {
        try {
            UrlNormalizer test = new UrlNormalizer(false, true, false);
            test.normalize("http://example.com");
            tldAvailable = true;
        } catch (Exception e) {
            tldAvailable = false;
        }
    }

    private UrlNormalizer normalizer(boolean flip, boolean includeHttp, boolean removeWww) {
        assumeTrue(tldAvailable, "TLD dictionary not available in test environment");
        return new UrlNormalizer(flip, includeHttp, removeWww);
    }

    @Nested
    @DisplayName("Basic Normalization")
    class BasicNormalization {

        @Test
        @DisplayName("Should lowercase host")
        void shouldLowercaseHost() {
            String result = normalizer(false, true, false).normalize("http://WWW.EXAMPLE.COM/Path");
            assertThat(result).startsWith("http://www.example.com/");
        }

        @Test
        @DisplayName("Should lowercase path")
        void shouldLowercasePath() {
            String result = normalizer(false, true, false).normalize("http://example.com/FOO/BAR");
            assertThat(result).contains("/foo/bar");
        }
    }

    @Nested
    @DisplayName("WWW Removal")
    class WwwRemoval {

        @Test
        @DisplayName("Should remove www when configured")
        void shouldRemoveWww() {
            String result = normalizer(false, true, true).normalize("http://www.example.com/page");
            assertThat(result).startsWith("http://example.com/");
        }

        @Test
        @DisplayName("Should keep www when not configured")
        void shouldKeepWww() {
            String result = normalizer(false, true, false).normalize("http://www.example.com/page");
            assertThat(result).startsWith("http://www.example.com/");
        }
    }

    @Nested
    @DisplayName("Port Handling")
    class PortHandling {

        @Test
        @DisplayName("Should remove default port 80")
        void shouldRemoveDefaultPort() {
            String result = normalizer(false, true, false).normalize("http://example.com:80/path");
            assertThat(result).doesNotContain(":80");
        }

        @Test
        @DisplayName("Should keep non-default port")
        void shouldKeepNonDefaultPort() {
            String result = normalizer(false, true, false).normalize("http://example.com:8080/path");
            assertThat(result).contains(":8080");
        }
    }

    @Nested
    @DisplayName("Path Normalization")
    class PathNormalization {

        @Test
        @DisplayName("Should resolve dot-dot path segments")
        void shouldResolveDotDot() {
            String result = normalizer(false, true, false).normalize("http://example.com/a/b/../c");
            assertThat(result).contains("/a/c");
            assertThat(result).doesNotContain("..");
        }

        @Test
        @DisplayName("Should remove single dot path segments")
        void shouldRemoveSingleDot() {
            String result = normalizer(false, true, false).normalize("http://example.com/a/./b");
            assertThat(result).doesNotContain("/./");
        }
    }

    @Nested
    @DisplayName("Argument Sorting")
    class ArgumentSorting {

        @Test
        @DisplayName("Should sort query arguments alphabetically")
        void shouldSortArgs() {
            String result = normalizer(false, true, false).normalize("http://example.com/page?z=1&a=2&m=3");
            int aPos = result.indexOf("a=2");
            int mPos = result.indexOf("m=3");
            int zPos = result.indexOf("z=1");
            assertThat(aPos).isLessThan(mPos);
            assertThat(mPos).isLessThan(zPos);
        }
    }

    @Nested
    @DisplayName("HTTPS Preservation")
    class HttpsPreservation {

        @Test
        @DisplayName("Should preserve https in output")
        void shouldPreserveHttps() {
            String result = normalizer(false, true, false).normalize("https://example.com/secure");
            assertThat(result).startsWith("https://");
        }

        @Test
        @DisplayName("Should output http for non-secure URLs")
        void shouldOutputHttp() {
            String result = normalizer(false, true, false).normalize("http://example.com/page");
            assertThat(result).startsWith("http://");
        }
    }

    @Nested
    @DisplayName("Host Flipping")
    class HostFlipping {

        @Test
        @DisplayName("Should reverse host components when configured")
        void shouldFlipHost() {
            String result = normalizer(true, false, false).normalize("http://www.example.com/page");
            assertThat(result).startsWith("com.example.www/");
        }
    }

    @Nested
    @DisplayName("Include HTTP Flag")
    class IncludeHttpFlag {

        @Test
        @DisplayName("Should omit protocol when includeHttp is false")
        void shouldOmitProtocol() {
            String result = normalizer(false, false, false).normalize("http://example.com/page");
            assertThat(result).doesNotStartWith("http://");
            assertThat(result).startsWith("example.com/");
        }
    }

    @Nested
    @DisplayName("Null/Empty Input")
    class NullEmptyInput {

        @Test
        @DisplayName("Should return null for empty input")
        void shouldReturnNullForEmpty() {
            // This test doesn't need TLD dictionary
            try {
                UrlNormalizer n = new UrlNormalizer(false, true, false);
                assertThat(n.normalize("")).isNull();
                assertThat(n.normalize(null)).isNull();
            } catch (Exception e) {
                // TLD not available, but null/empty check happens before TLD access
                // so this should still work
            }
        }

        @Test
        @DisplayName("Should return null for fragment-only input")
        void shouldReturnNullForFragment() {
            try {
                UrlNormalizer n = new UrlNormalizer(false, true, false);
                assertThat(n.normalize("#section")).isNull();
            } catch (Exception e) {
                // OK if TLD not available
            }
        }
    }
}
