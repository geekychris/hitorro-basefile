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

import com.hitorro.util.basefile.fs.file.FileFile;
import com.hitorro.util.basefile.fs.file.FileFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BaseFile")
class BaseFileTest {

    @TempDir
    Path tempDir;

    FileFileSystem fs;

    @BeforeEach
    void setUp() {
        fs = new FileFileSystem(tempDir.toFile());
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityAndHashCode {

        @Test
        @DisplayName("Should not throw NPE when comparing with null")
        void shouldNotThrowNpeForNull() {
            FileFile file = fs.getFile("test.txt");
            assertThat(file.equals(null)).isFalse();
        }

        @Test
        @DisplayName("Should be equal to same path")
        void shouldBeEqualToSamePath() {
            FileFile a = fs.getFile("test.txt");
            FileFile b = fs.getFile("test.txt");
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Should not be equal to different path")
        void shouldNotBeEqualToDifferentPath() {
            FileFile a = fs.getFile("a.txt");
            FileFile b = fs.getFile("b.txt");
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("Should not be equal to non-BaseFile")
        void shouldNotBeEqualToNonBaseFile() {
            FileFile a = fs.getFile("test.txt");
            assertThat(a.equals("not a file")).isFalse();
        }
    }

    @Nested
    @DisplayName("Read/Write Roundtrip")
    class ReadWriteRoundtrip {

        @Test
        @DisplayName("Should write and read string")
        void shouldWriteAndReadString() throws IOException {
            FileFile file = fs.getFile("test.txt");
            file.writeString("Hello, World!");
            assertThat(file.readString()).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("Should write and read long")
        void shouldWriteAndReadLong() throws IOException {
            FileFile file = fs.getFile("test.dat");
            file.writeLong(42L);
            assertThat(file.readLong()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Should write and read bytes")
        void shouldWriteAndReadBytes() throws IOException {
            FileFile file = fs.getFile("test.bin");
            file.writeString("bytes test");
            byte[] bytes = file.getBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("bytes test");
        }

        @Test
        @DisplayName("Should write and read string list")
        void shouldWriteAndReadStringList() throws IOException {
            FileFile file = fs.getFile("lines.txt");
            file.writeStringListToFile(List.of("line1", "line2", "line3"));
            List<String> lines = file.getLines();
            assertThat(lines).containsExactly("line1", "line2", "line3");
        }
    }

    @Nested
    @DisplayName("Compression Transparency")
    class CompressionTransparency {

        @Test
        @DisplayName("Should transparently compress and decompress gz files")
        void shouldTransparentlyHandleGz() throws IOException {
            FileFile file = fs.getFile("data.txt.gz");
            file.writeString("compressed content");
            assertThat(file.readString()).isEqualTo("compressed content");
            // Compressed file should exist and be smaller or different from raw
            assertThat(file.exists()).isTrue();
        }

        @Test
        @DisplayName("Should transparently compress and decompress zstd files")
        void shouldTransparentlyHandleZstd() throws IOException {
            FileFile file = fs.getFile("data.txt.zstd");
            file.writeString("zstandard compressed content");
            assertThat(file.readString()).isEqualTo("zstandard compressed content");
            assertThat(file.exists()).isTrue();
        }

        @Test
        @DisplayName("Should detect compression type from extension")
        void shouldDetectCompressionType() {
            assertThat(fs.getFile("data.gz").getCompressionType()).isEqualTo(CompressionType.gz);
            assertThat(fs.getFile("data.bz2").getCompressionType()).isEqualTo(CompressionType.bz2);
            assertThat(fs.getFile("data.zstd").getCompressionType()).isEqualTo(CompressionType.zstd);
            assertThat(fs.getFile("data.csv").getCompressionType()).isEqualTo(CompressionType.none);
        }

        @Test
        @DisplayName("Should get file extension ignoring compression")
        void shouldGetExtensionIgnoringCompression() {
            FileFile file = fs.getFile("data.csv.gz");
            assertThat(file.getFileExtension(true)).isEqualTo("csv");
            assertThat(file.getFileExtension(false)).isEqualTo("gz");
        }
    }

    @Nested
    @DisplayName("Directory Operations")
    class DirectoryOperations {

        @Test
        @DisplayName("Should create directory")
        void shouldCreateDirectory() {
            FileFile dir = fs.getFile("subdir");
            assertThat(dir.mkdir()).isTrue();
            assertThat(dir.isDir()).isTrue();
        }

        @Test
        @DisplayName("Should list files")
        void shouldListFiles() throws IOException {
            FileFile dir = fs.getFile("listdir");
            dir.mkdir();
            dir.getChild("a.txt").writeString("a");
            dir.getChild("b.txt").writeString("b");
            dir.getChild("c.json").writeString("{}");

            BaseFile[] files = dir.listFiles();
            assertThat(files).hasSize(3);
        }

        @Test
        @DisplayName("Should list files with predicate filter")
        void shouldListFilesWithPredicate() throws IOException {
            FileFile dir = fs.getFile("filterdir");
            dir.mkdir();
            dir.getChild("a.txt").writeString("a");
            dir.getChild("b.json").writeString("{}");

            BaseFile[] txtFiles = dir.listFiles(f -> f.getName().endsWith(".txt"));
            assertThat(txtFiles).hasSize(1);
            assertThat(txtFiles[0].getName()).isEqualTo("a.txt");
        }

        @Test
        @DisplayName("Should list files with glob pattern")
        void shouldListFilesWithGlob() throws IOException {
            FileFile dir = fs.getFile("globdir");
            dir.mkdir();
            dir.getChild("data-2024.csv").writeString("a");
            dir.getChild("data-2025.csv").writeString("b");
            dir.getChild("report.pdf").writeString("c");

            BaseFile[] csvFiles = dir.listFiles("*.csv");
            assertThat(csvFiles).hasSize(2);
        }

        @Test
        @DisplayName("Should delete directory contents recursively")
        void shouldDeleteDirContentsRecursively() throws IOException {
            FileFile dir = fs.getFile("deldir");
            dir.mkdir();
            FileFile sub = dir.getChild("sub");
            sub.mkdir();
            sub.getChild("file.txt").writeString("data");
            dir.getChild("top.txt").writeString("top");

            dir.deleteContentOfDir(true);

            // The directory itself should still exist but be empty
            assertThat(dir.exists()).isTrue();
            BaseFile[] remaining = dir.listFiles();
            assertThat(remaining).isEmpty();
        }

        @Test
        @DisplayName("Should copy directory")
        void shouldCopyDirectory() throws IOException {
            FileFile src = fs.getFile("srcdir");
            src.mkdir();
            src.getChild("a.txt").writeString("aaa");
            src.getChild("b.txt").writeString("bbb");

            FileFile dst = fs.getFile("dstdir");
            dst.mkdir();
            src.copyDirectory(dst, f -> true);

            assertThat(dst.getChild("a.txt").readString()).isEqualTo("aaa");
            assertThat(dst.getChild("b.txt").readString()).isEqualTo("bbb");
        }
    }

    @Nested
    @DisplayName("File Metadata")
    class FileMetadata {

        @Test
        @DisplayName("Should check existence")
        void shouldCheckExistence() throws IOException {
            FileFile file = fs.getFile("exists.txt");
            assertThat(file.exists()).isFalse();
            file.writeString("data");
            assertThat(file.exists()).isTrue();
        }

        @Test
        @DisplayName("Should distinguish files from directories")
        void shouldDistinguishFilesFromDirs() throws IOException {
            FileFile file = fs.getFile("file.txt");
            file.writeString("data");
            FileFile dir = fs.getFile("dir");
            dir.mkdir();

            assertThat(file.isFile()).isTrue();
            assertThat(file.isDir()).isFalse();
            assertThat(dir.isDir()).isTrue();
            assertThat(dir.isFile()).isFalse();
        }

        @Test
        @DisplayName("Should return file length")
        void shouldReturnFileLength() throws IOException {
            FileFile file = fs.getFile("size.txt");
            file.writeString("12345");
            assertThat(file.length()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should return file name")
        void shouldReturnFileName() {
            FileFile file = fs.getFile("myfile.txt");
            assertThat(file.getName()).isEqualTo("myfile.txt");
        }

        @Test
        @DisplayName("Should return extension")
        void shouldReturnExtension() {
            FileFile file = fs.getFile("data.json");
            assertThat(file.getExtension()).isEqualTo("json");
        }

        @Test
        @DisplayName("Should return name sans extension")
        void shouldReturnNameSansExtension() {
            FileFile file = fs.getFile("report.pdf");
            assertThat(file.getNameSansExtension()).isEqualTo("report");
        }

        @Test
        @DisplayName("Should check missing or empty")
        void shouldCheckMissingOrEmpty() throws IOException {
            FileFile missing = fs.getFile("missing.txt");
            assertThat(missing.missingOrEmpty()).isTrue();

            FileFile file = fs.getFile("notempty.txt");
            file.writeString("data");
            assertThat(file.missingOrEmpty()).isFalse();
        }

        @Test
        @DisplayName("Should compute digest")
        void shouldComputeDigest() throws IOException {
            FileFile file = fs.getFile("digest.txt");
            file.writeString("hello");
            String digest = file.getDigest();
            assertThat(digest).isNotNull().isNotEmpty();
        }
    }

    @Nested
    @DisplayName("File Navigation")
    class FileNavigation {

        @Test
        @DisplayName("Should navigate to child")
        void shouldNavigateToChild() throws IOException {
            FileFile dir = fs.getFile("nav");
            dir.mkdir();
            FileFile child = dir.getChild("file.txt");
            child.writeString("data");
            assertThat(child.exists()).isTrue();
        }

        @Test
        @DisplayName("Should navigate to child with format args")
        void shouldNavigateToChildWithFormatArgs() {
            FileFile dir = fs.getFile("nav2");
            FileFile child = dir.getChild("file-%s.txt", "001");
            assertThat(child.getName()).isEqualTo("file-001.txt");
        }

        @Test
        @DisplayName("Should navigate to parent")
        void shouldNavigateToParent() {
            FileFile file = fs.getFile("parent/child.txt");
            FileFile parent = file.getParent();
            assertThat(parent.getRelativePath()).isEqualTo("parent");
        }

        @Test
        @DisplayName("Should navigate to peer with different extension")
        void shouldNavigateToPeerExtension() {
            FileFile file = fs.getFile("dir/data.csv");
            BaseFile peer = file.getPeerExtension("json");
            assertThat(peer.getName()).isEqualTo("data.json");
        }
    }

    @Nested
    @DisplayName("Copy Operations")
    class CopyOperations {

        @Test
        @DisplayName("Should copy file")
        void shouldCopyFile() throws IOException {
            FileFile src = fs.getFile("src.txt");
            src.writeString("copy me");
            FileFile dst = fs.getFile("dst.txt");
            dst.copy(src);
            assertThat(dst.readString()).isEqualTo("copy me");
        }

        @Test
        @DisplayName("Should copy file content via BaseFile copy")
        void shouldCopyFileContent() throws IOException {
            FileFile src = fs.getFile("src2.txt");
            src.writeString("copy this content");
            FileFile dst = fs.getFile("dst2.txt");
            dst.copy(src);
            assertThat(dst.readString()).isEqualTo("copy this content");
            assertThat(dst.length()).isEqualTo(src.length());
        }
    }

    @Nested
    @DisplayName("Static Utilities")
    class StaticUtilities {

        @Test
        @DisplayName("notNullAndExists should return false for null")
        void notNullAndExistsShouldReturnFalseForNull() {
            assertThat(BaseFile.notNullAndExists(null)).isFalse();
        }

        @Test
        @DisplayName("notNullAndExists should return false for nonexistent file")
        void notNullAndExistsShouldReturnFalseForNonexistent() {
            assertThat(BaseFile.notNullAndExists(fs.getFile("nope.txt"))).isFalse();
        }

        @Test
        @DisplayName("notNullAndExists should return true for existing file")
        void notNullAndExistsShouldReturnTrueForExisting() throws IOException {
            FileFile file = fs.getFile("yes.txt");
            file.writeString("data");
            assertThat(BaseFile.notNullAndExists(file)).isTrue();
        }

        @Test
        @DisplayName("notNullAndExistsAndContainsData should check size")
        void notNullAndExistsAndContainsDataShouldCheckSize() throws IOException {
            assertThat(BaseFile.notNullAndExistsAndContainsData(null)).isFalse();

            FileFile file = fs.getFile("data.txt");
            file.writeString("content");
            assertThat(BaseFile.notNullAndExistsAndContainsData(file)).isTrue();
        }
    }
}
