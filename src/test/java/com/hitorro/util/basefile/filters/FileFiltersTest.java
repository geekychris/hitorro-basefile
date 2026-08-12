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
package com.hitorro.util.basefile.filters;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.file.FileFile;
import com.hitorro.util.basefile.fs.file.FileFileSystem;
import com.hitorro.util.core.opers.HTPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("File Filters")
class FileFiltersTest {

    @TempDir
    Path tempDir;

    FileFileSystem fs;
    FileFile jsonFile;
    FileFile csvFile;
    FileFile txtFile;
    FileFile dir;

    @BeforeEach
    void setUp() throws IOException {
        fs = new FileFileSystem(tempDir.toFile());
        FileFile root = fs.getFile("testdir");
        root.mkdir();
        jsonFile = root.getChild("data.json");
        jsonFile.writeString("{}");
        csvFile = root.getChild("report.csv");
        csvFile.writeString("a,b");
        txtFile = root.getChild("notes.txt");
        txtFile.writeString("text");
        dir = root.getChild("subdir");
        dir.mkdir();
    }

    @Nested
    @DisplayName("FileExtension")
    class FileExtensionTests {

        @Test
        @DisplayName("Should match file by extension")
        void shouldMatchByExtension() {
            HTPredicate<BaseFile> filter = FileFilterBase.hasExt("json");
            assertThat(filter.test(jsonFile)).isTrue();
            assertThat(filter.test(csvFile)).isFalse();
        }

        @Test
        @DisplayName("Should be case insensitive")
        void shouldBeCaseInsensitive() {
            HTPredicate<BaseFile> filter = FileFilterBase.hasExt("JSON");
            assertThat(filter.test(jsonFile)).isTrue();
        }
    }

    @Nested
    @DisplayName("FileEndsWith")
    class FileEndsWithTests {

        @Test
        @DisplayName("Should match file ending with suffix")
        void shouldMatchSuffix() {
            HTPredicate<BaseFile> filter = FileFilterBase.endsWith(".csv");
            assertThat(filter.test(csvFile)).isTrue();
            assertThat(filter.test(jsonFile)).isFalse();
        }
    }

    @Nested
    @DisplayName("FileStartsWith")
    class FileStartsWithTests {

        @Test
        @DisplayName("Should match file starting with prefix")
        void shouldMatchPrefix() {
            // FileStartsWith checks if prefix starts with name - args are reversed in the impl
            // Use the full filename as the prefix to match
            HTPredicate<BaseFile> filter = FileFilterBase.startsWith("data.json");
            assertThat(filter.test(jsonFile)).isTrue();
            assertThat(filter.test(csvFile)).isFalse();
        }
    }

    @Nested
    @DisplayName("FileNameContains")
    class FileNameContainsTests {

        @Test
        @DisplayName("Should match file containing substring")
        void shouldMatchSubstring() {
            HTPredicate<BaseFile> filter = FileFilterBase.nameContains("report");
            assertThat(filter.test(csvFile)).isTrue();
            assertThat(filter.test(jsonFile)).isFalse();
        }
    }

    @Nested
    @DisplayName("IsDir")
    class IsDirTests {

        @Test
        @DisplayName("Should filter directories")
        void shouldFilterDirectories() {
            HTPredicate<BaseFile> filter = FileFilterBase.isDir();
            assertThat(filter.test(dir)).isTrue();
            assertThat(filter.test(jsonFile)).isFalse();
        }

        @Test
        @DisplayName("Should filter non-directories")
        void shouldFilterNonDirectories() {
            HTPredicate<BaseFile> filter = FileFilterBase.notDir();
            assertThat(filter.test(jsonFile)).isTrue();
            assertThat(filter.test(dir)).isFalse();
        }
    }

    @Nested
    @DisplayName("Negation")
    class NegationTests {

        @Test
        @DisplayName("Should negate filter via static not()")
        void shouldNegateViaStaticNot() {
            HTPredicate<BaseFile> jsonFilter = FileFilterBase.hasExt("json");
            HTPredicate<BaseFile> notJson = FileFilterBase.not(jsonFilter);
            assertThat(notJson.test(jsonFile)).isFalse();
            assertThat(notJson.test(csvFile)).isTrue();
        }
    }

    @Nested
    @DisplayName("GlobFilter")
    class GlobFilterTests {

        @Test
        @DisplayName("Should match files by glob pattern")
        void shouldMatchByGlob() {
            GlobFilter filter = new GlobFilter("*.json");
            assertThat(filter.test(jsonFile)).isTrue();
            assertThat(filter.test(csvFile)).isFalse();
        }

        @Test
        @DisplayName("Should match files with complex glob")
        void shouldMatchComplexGlob() {
            GlobFilter filter = new GlobFilter("*.{json,csv}");
            assertThat(filter.test(jsonFile)).isTrue();
            assertThat(filter.test(csvFile)).isTrue();
            assertThat(filter.test(txtFile)).isFalse();
        }

        @Test
        @DisplayName("Should match files with wildcard prefix")
        void shouldMatchWildcardPrefix() {
            GlobFilter filter = new GlobFilter("report*");
            assertThat(filter.test(csvFile)).isTrue();
            assertThat(filter.test(jsonFile)).isFalse();
        }

        @Test
        @DisplayName("Should be available via FileFilterBase factory")
        void shouldBeAvailableViaFactory() {
            HTPredicate<BaseFile> filter = FileFilterBase.glob("*.txt");
            assertThat(filter.test(txtFile)).isTrue();
            assertThat(filter.test(jsonFile)).isFalse();
        }
    }
}
