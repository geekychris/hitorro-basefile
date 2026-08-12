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
package com.hitorro.util.basefile.fs.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileFile")
class FileFileTest {

    @TempDir
    Path tempDir;

    FileFileSystem fs;

    @BeforeEach
    void setUp() {
        fs = new FileFileSystem(tempDir.toFile());
    }

    @Nested
    @DisplayName("Path Integration")
    class PathIntegration {

        @Test
        @DisplayName("Should convert to java.nio.file.Path")
        void shouldConvertToPath() throws IOException {
            FileFile file = fs.getFile("test.txt");
            file.writeString("data");
            Path path = file.toPath();
            assertThat(path).isNotNull();
            assertThat(path.toFile()).isEqualTo(file.getJavaFile());
        }

        @Test
        @DisplayName("toPath should work for non-existent files")
        void toPathShouldWorkForNonExistent() {
            FileFile file = fs.getFile("nonexistent.txt");
            Path path = file.toPath();
            assertThat(path).isNotNull();
            assertThat(path.getFileName().toString()).isEqualTo("nonexistent.txt");
        }
    }

    @Nested
    @DisplayName("Local File Properties")
    class LocalFileProperties {

        @Test
        @DisplayName("Should be local filesystem")
        void shouldBeLocal() {
            FileFile file = fs.getFile("test.txt");
            assertThat(file.isLocal()).isTrue();
        }

        @Test
        @DisplayName("Should support soft links")
        void shouldSupportSoftLinks() {
            FileFile file = fs.getFile("test.txt");
            assertThat(file.supportsSoftLink()).isTrue();
        }

        @Test
        @DisplayName("Should return underlying Java File")
        void shouldReturnJavaFile() {
            FileFile file = fs.getFile("test.txt");
            assertThat(file.getJavaFile()).isNotNull();
            assertThat(file.getLocalFileIfPossible()).isNotNull();
            assertThat(file.getLocalFileIfPossible()).isEqualTo(file.getJavaFile());
        }
    }

    @Nested
    @DisplayName("Directory Listing Edge Cases")
    class DirectoryListing {

        @Test
        @DisplayName("Should return null for nonexistent directory listing")
        void shouldReturnNullForNonexistentDir() throws IOException {
            FileFile dir = fs.getFile("nonexistent");
            FileFile[] files = dir.listFiles(f -> true);
            assertThat(files).isNull();
        }

        @Test
        @DisplayName("Should return empty array for empty directory")
        void shouldReturnEmptyForEmptyDir() throws IOException {
            FileFile dir = fs.getFile("emptydir");
            dir.mkdir();
            FileFile[] files = dir.listFiles(f -> true);
            assertThat(files).isEmpty();
        }
    }

    @Nested
    @DisplayName("Rename and Delete")
    class RenameAndDelete {

        @Test
        @DisplayName("Should rename file within same filesystem")
        void shouldRenameFile() throws IOException {
            FileFile src = fs.getFile("before.txt");
            src.writeString("rename me");
            FileFile dst = fs.getFile("after.txt");
            src.renameTo(dst);
            assertThat(dst.exists()).isTrue();
            assertThat(dst.readString()).isEqualTo("rename me");
        }

        @Test
        @DisplayName("Should delete file")
        void shouldDeleteFile() throws IOException {
            FileFile file = fs.getFile("deleteme.txt");
            file.writeString("gone");
            assertThat(file.exists()).isTrue();
            file.delete();
            assertThat(file.exists()).isFalse();
        }

        @Test
        @DisplayName("Should delete directory recursively")
        void shouldDeleteDirectoryRecursively() throws IOException {
            FileFile dir = fs.getFile("deldir");
            dir.mkdir();
            dir.getChild("sub").mkdir();
            dir.getChild("sub").getChild("file.txt").writeString("deep");
            dir.delete();
            assertThat(dir.exists()).isFalse();
        }
    }

    @Nested
    @DisplayName("Temp File")
    class TempFile {

        @Test
        @DisplayName("Should create temp file with .tmp extension")
        void shouldCreateTempFile() {
            FileFile file = fs.getFile("original.txt");
            FileFile temp = file.getTempFile();
            assertThat(temp.getName()).isEqualTo("original.txt.tmp");
        }
    }

    @Nested
    @DisplayName("Modification Time")
    class ModificationTime {

        @Test
        @DisplayName("Should set and get last modified time")
        void shouldSetAndGetModifiedTime() throws IOException {
            FileFile file = fs.getFile("time.txt");
            file.writeString("data");
            long newTime = 1000000000L;
            file.setLastModified(newTime);
            assertThat(file.getModifiedTime()).isEqualTo(newTime);
        }

        @Test
        @DisplayName("Should touch file to update modification time")
        void shouldTouchFile() throws IOException {
            FileFile file = fs.getFile("touch.txt");
            file.writeString("data");
            file.setLastModified(1000000000L);
            long before = file.getModifiedTime();
            file.touch();
            assertThat(file.getModifiedTime()).isGreaterThan(before);
        }
    }
}
