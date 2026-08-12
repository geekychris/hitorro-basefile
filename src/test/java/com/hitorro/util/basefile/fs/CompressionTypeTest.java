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
package com.hitorro.util.basefile.fs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CompressionType")
class CompressionTypeTest {

    @Nested
    @DisplayName("Lookup by Name")
    class LookupByName {

        @Test
        @DisplayName("Should return gz for 'gz'")
        void shouldReturnGzForGz() {
            assertThat(CompressionType.getFilterByName("gz")).isEqualTo(CompressionType.gz);
        }

        @Test
        @DisplayName("Should return bz2 for 'bz2'")
        void shouldReturnBz2ForBz2() {
            assertThat(CompressionType.getFilterByName("bz2")).isEqualTo(CompressionType.bz2);
        }

        @Test
        @DisplayName("Should return zstd for 'zstd'")
        void shouldReturnZstdForZstd() {
            assertThat(CompressionType.getFilterByName("zstd")).isEqualTo(CompressionType.zstd);
        }

        @Test
        @DisplayName("Should return none for null")
        void shouldReturnNoneForNull() {
            assertThat(CompressionType.getFilterByName(null)).isEqualTo(CompressionType.none);
        }

        @Test
        @DisplayName("Should return none for empty string")
        void shouldReturnNoneForEmpty() {
            assertThat(CompressionType.getFilterByName("")).isEqualTo(CompressionType.none);
        }

        @Test
        @DisplayName("Should return none for unknown extension")
        void shouldReturnNoneForUnknown() {
            assertThat(CompressionType.getFilterByName("csv")).isEqualTo(CompressionType.none);
        }
    }

    @Nested
    @DisplayName("Lookup by Filename")
    class LookupByFilename {

        @Test
        @DisplayName("Should detect gz from filename")
        void shouldDetectGzFromFilename() {
            assertThat(CompressionType.getFilterByFileName("data.csv.gz")).isEqualTo(CompressionType.gz);
        }

        @Test
        @DisplayName("Should detect bz2 from filename")
        void shouldDetectBz2FromFilename() {
            assertThat(CompressionType.getFilterByFileName("data.tar.bz2")).isEqualTo(CompressionType.bz2);
        }

        @Test
        @DisplayName("Should detect zstd from filename")
        void shouldDetectZstdFromFilename() {
            assertThat(CompressionType.getFilterByFileName("data.json.zstd")).isEqualTo(CompressionType.zstd);
        }

        @Test
        @DisplayName("Should return none for uncompressed filename")
        void shouldReturnNoneForUncompressed() {
            assertThat(CompressionType.getFilterByFileName("data.csv")).isEqualTo(CompressionType.none);
        }
    }

    @Nested
    @DisplayName("Extension")
    class Extension {

        @Test
        @DisplayName("Should return correct extensions")
        void shouldReturnCorrectExtensions() {
            assertThat(CompressionType.gz.getExtension()).isEqualTo("gz");
            assertThat(CompressionType.bz2.getExtension()).isEqualTo("bz2");
            assertThat(CompressionType.zstd.getExtension()).isEqualTo("zstd");
            assertThat(CompressionType.none.getExtension()).isEqualTo("null");
        }
    }

    @Nested
    @DisplayName("Name Sans Compression")
    class NameSansCompression {

        @Test
        @DisplayName("Should strip gz extension")
        void shouldStripGzExtension() {
            assertThat(CompressionType.gz.getNameSansCompression("data.csv.gz")).isEqualTo("data.csv");
        }

        @Test
        @DisplayName("Should strip bz2 extension")
        void shouldStripBz2Extension() {
            assertThat(CompressionType.bz2.getNameSansCompression("data.tar.bz2")).isEqualTo("data.tar");
        }

        @Test
        @DisplayName("Should strip zstd extension")
        void shouldStripZstdExtension() {
            assertThat(CompressionType.zstd.getNameSansCompression("data.json.zstd")).isEqualTo("data.json");
        }

        @Test
        @DisplayName("Should return same name for none")
        void shouldReturnSameNameForNone() {
            assertThat(CompressionType.none.getNameSansCompression("data.csv")).isEqualTo("data.csv");
        }
    }

    @Nested
    @DisplayName("Round-trip Compression")
    class RoundTrip {

        private byte[] roundTrip(CompressionType ct, byte[] data) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (OutputStream os = ct.getOutputStreamCompressed(baos)) {
                os.write(data);
            }
            byte[] compressed = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            try (InputStream is = ct.getInputCompressed(bais)) {
                return is.readAllBytes();
            }
        }

        @Test
        @DisplayName("Should round-trip through gzip")
        void shouldRoundTripGzip() throws Exception {
            byte[] original = "Hello, gzip world!".getBytes(StandardCharsets.UTF_8);
            assertThat(roundTrip(CompressionType.gz, original)).isEqualTo(original);
        }

        @Test
        @DisplayName("Should round-trip through bz2")
        void shouldRoundTripBz2() throws Exception {
            byte[] original = "Hello, bzip2 world!".getBytes(StandardCharsets.UTF_8);
            assertThat(roundTrip(CompressionType.bz2, original)).isEqualTo(original);
        }

        @Test
        @DisplayName("Should round-trip through zstd")
        void shouldRoundTripZstd() throws Exception {
            byte[] original = "Hello, zstandard world!".getBytes(StandardCharsets.UTF_8);
            assertThat(roundTrip(CompressionType.zstd, original)).isEqualTo(original);
        }

        @Test
        @DisplayName("Should pass through for none")
        void shouldPassThroughForNone() throws Exception {
            byte[] original = "Hello, no compression!".getBytes(StandardCharsets.UTF_8);
            assertThat(roundTrip(CompressionType.none, original)).isEqualTo(original);
        }

        @Test
        @DisplayName("Should handle large data through zstd")
        void shouldHandleLargeDataThroughZstd() throws Exception {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("Line ").append(i).append(": some repetitive data for compression\n");
            }
            byte[] original = sb.toString().getBytes(StandardCharsets.UTF_8);
            assertThat(roundTrip(CompressionType.zstd, original)).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Size")
    class Size {

        @Test
        @DisplayName("Should have correct count of registered types")
        void shouldHaveCorrectCount() {
            assertThat(CompressionType.size()).isEqualTo(4); // gz, bz2, zstd, none
        }
    }
}
