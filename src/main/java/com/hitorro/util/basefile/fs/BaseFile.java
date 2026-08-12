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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.hash.TLongLongHashMap;
import com.hitorro.util.basefile.tools.BaseLock;
import com.hitorro.util.core.ArrayUtilCore;
import com.hitorro.util.core.ConsoleCore;
import com.hitorro.util.core.Log;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import com.hitorro.util.core.params.HTProperties;
import com.hitorro.util.core.string.FmtCore;
import com.hitorro.util.core.string.StringUtilCore;
import com.hitorro.util.io.FileUtilCore;
import com.hitorro.util.io.IOUtil;
import com.hitorro.util.io.largedata.compressedstreams.CInputStream;
import com.hitorro.util.io.largedata.compressedstreams.COutputStream;
import com.hitorro.util.io.largedata.compressedstreams.RAMInputStream;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;


/**
 * User: chris
 */
public abstract class BaseFile<F extends BaseFile, FS extends BaseFileSystem> {
    public static BaseFile[] EmptyList = new BaseFile[0];

    private static long fCounter = 0;
    protected String name = null;
    protected String path;
    FS fs;

    private static synchronized long fileCounter() {
        return fCounter++;
    }

    /**
     * Util method to determine if we have a file.
     *
     * @param bf
     * @return
     */
    public static boolean notNullAndExists(BaseFile bf) {
        return bf != null && bf.exists();
    }

    /**
     * Util method to determine if we have a file and that it contains data.
     *
     * @param bf
     * @return
     */
    public static boolean notNullAndExistsAndContainsData(BaseFile bf) {
        return bf != null && bf.exists() && bf.length() > 0;
    }

    public String getDigest() throws IOException {
        try (DataInputStream dis = getDataInputStream()) {
            return DigestUtils.md5Hex(dis);
        }
    }

    public int hashCode() {
        return StringUtilCore.mergeHashcodes(name, path);
    }

    public boolean equals(Object o) {
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        if (o instanceof BaseFile) {
            BaseFile other = (BaseFile) o;
            return StringUtilCore.equals(name, other.name, false) &&
                    StringUtilCore.equals(path, other.path, false);
        }
        return false;
    }

    public JsonNode getJsonNode() throws Exception {
        return getJsonNode(null);
    }

    /**
     * @deprecated Use getJsonNode() directly instead
     */
    @Deprecated
    public JsonNode getJVS() throws Exception {
        return getJsonNode();
    }

    public BaseFile getTempFileWithFromPeerFileWithExtension(String ext) {
        return getTempFileWithFromDirectoryExtension(this.getParent(), ext);
    }

    public BaseFile getTempFileWithFromDirectoryExtension(
            BaseFile directory, String ext) {
        return directory.getChild("%s-%s.%s", fileCounter(), System.currentTimeMillis(), ext);
    }

    public boolean swap(BaseFile b) {
        BaseFile a = this;
        BaseFile temp = getTempFileWithFromPeerFileWithExtension("tmp");
        try {
            a.renameTo(temp);
        } catch (SecurityException se) {
            return false;
        } catch (IOException e) {
            Log.filesystem.error("swap: IO error renaming a to temp: %s", e.getMessage());
            return false;
        }
        try {
            b.renameTo(a);
        } catch (SecurityException se) {
            try {
                // try to revert back
                temp.renameTo(a);
                return false;
            } catch (SecurityException se2) {
                return false;
            } catch (IOException e2) {
                Log.filesystem.error("swap: IO error reverting rename: %s", e2.getMessage());
                return false;
            }
        } catch (IOException e) {
            Log.filesystem.error("swap: IO error renaming b to a: %s", e.getMessage());
            return false;
        }

        try {
            temp.renameTo(b);
        } catch (SecurityException se) {
            return false;
        } catch (IOException e) {
            Log.filesystem.error("swap: IO error renaming temp to b: %s", e.getMessage());
            return false;
        }
        return true;
    }

    public File getLocalFileIfPossible() {
        return null;
    }

    public JsonNode getJsonNode(String type) throws Exception {
        try (java.io.Reader r = this.getReader()) {
            if (r == null) return null;
            return new ObjectMapper().readTree(r);
        }
    }

    public int compareTo(BaseFile to) {
        return this.getAbsolutePath().compareTo(to.getAbsolutePath());
    }

    public boolean supportsSoftLink() {
        return false;
    }

    public boolean linkTo(BaseFile bf) throws IOException {
        return false;
    }

    public void writeJson(JsonNode jn) throws IOException {
        try (OutputStream os = this.getOutputStream()) {
            new ObjectMapper().writeValue(os, jn);
        }
    }

    public JsonNode readJsonElement() throws Exception {
        try (java.io.Reader r = this.getReader()) {
            return new ObjectMapper().readTree(r);
        }
    }

    public boolean missingOrEmpty() {
        if (!this.exists()) {
            return true;
        }
        return this.length() == 0;
    }

    public CompressionType getCompressionType() {
        return CompressionType.getFilterByName(this.getExtension());
    }


    public String getFileExtension(boolean ignoreCompression) {
        String p = this.getAbsolutePath();
        String ext = FileUtilCore.getFileExtension(p);
        if (ignoreCompression) {

            if (StringUtilCore.nullOrEmptyString(ext)) {
                return ext;
            }
            CompressionType ct = getCompressionType();

            if (ct == CompressionType.none) {
                return ext;
            }
            return FileUtilCore.getFileExtension(p.substring(0, p.length() - (ct.getExtension().length() + 1)));
        } else {
            return ext;
        }
    }

    public HTProperties getProperties() throws IOException {
        boolean isXML = this.path.endsWith("xmlprops");
        HTProperties props = new HTProperties();
        try (DataInputStream dis = this.getDataInputStream()) {
            props.readFile(dis, true, isXML);
        }
        return props;
    }

    public JsonNode getPropertiesAsJsonNode() throws IOException {
        HTProperties props = getProperties();
        if (props == null) {
            return null;
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, String> e : props.entrySet()) {
            node.put(e.getKey(), e.getValue());
        }
        return node;
    }

    public boolean writeProperties(HTProperties props) throws IOException {
        try (DataOutputStream dos = this.getDataOutputStream()) {
            props.write(dos);
        }
        return true;
    }


    public CInputStream getInputStreamRAM() throws IOException {
        try (DataInputStream dis = this.getDataInputStream()) {
            byte[] buffer = new byte[(int) this.length()];
            IOUtil.copyStream(dis, buffer);
            RAMInputStream is = new RAMInputStream(null);
            is.setBuffer(buffer);
            return is;
        }
    }

    public byte[] getBytes() throws IOException {
        try (DataInputStream dis = this.getDataInputStream()) {
            byte[] buffer = new byte[(int) this.length()];
            IOUtil.copyStream(dis, buffer);
            return buffer;
        }
    }

    public abstract void touch();

    /**
     * age of the file since it was last touched
     *
     * @return
     */
    public long ageOfFile() {
        return System.currentTimeMillis() - getModifiedTime();
    }

    public boolean writeString(String s) throws IOException {
        PrintWriter pw = getPrintWriter();
        if (pw == null) {
            return false;
        }
        pw.print(s);
        pw.flush();
        pw.close();
        return true;
    }

    public String readString() throws IOException {
        try (DataInputStream dis = this.getDataInputStream()) {
            StringBuilder sb = StringUtilCore.readStreamIntoBuilder(dis);
            return sb.toString();
        }
    }

    public Reader getReader() throws IOException {
        return new InputStreamReader(this.getDataInputStream(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public Reader getReader(String encoding) throws IOException {
        DataInputStream dis = this.getDataInputStream();
        return new InputStreamReader(dis, encoding);
    }

    public PrintStream getPrintStream() throws IOException {
        return new PrintStream(getDataOutputStream());
    }

    public PrintWriter getPrintWriter() throws IOException {
        DataOutputStream dos = getDataOutputStream();
        if (dos == null) {
            return null;
        }
        return new PrintWriter(dos);
    }

    public BaseFile getPeerExtension(String extension) {
        return getPeer(FileUtilCore.getPeerFromName(getName(), extension));
    }

    /**
     * Copy a complete directory to a target location
     *
     * @param to
     * @return
     * @throws IOException
     */
    public boolean copyDirectory(BaseFile to, Predicate<BaseFile> filter) throws IOException {
        BaseFile files[] = this.listFiles(filter);
        if (ArrayUtilCore.nullOrEmpty(files)) {
            return true;
        }
        for (BaseFile file : files) {
            if (file.isDir()) {
                if (!file.copyDirectory(to.getChild(file.getName()), filter)) {
                    return false;
                }
            } else {
                BaseFile toF = to.getChild(file.getName());
                if (!toF.copy(file)) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Copy the file
     *
     * @param copyFrom
     * @return
     */
    public boolean copy(BaseFile copyFrom) throws IOException {
        long size = copyFrom.length();
        // We must use the raw IO so that we are not using decompression and compression algorithms
        java.io.InputStream is = copyFrom.getInputStreamRaw();
        java.io.OutputStream os = getOutputStreamRaw();
        return checkFile(IOUtil.copyStream(is, os, size, true), copyFrom);
    }

    public String getNameSansExtension() {
        String name = getName();
        return FileUtilCore.getFileNameSansExtension(name);
    }

    public boolean copyFrom(File f, boolean useTemp, boolean compress) throws IOException {
        if (useTemp) {
            return copyFromFileViaTemp(f, compress);
        }
        return ensureFilesSameSize(IOUtil.copyStream(FileUtilCore.getBufferedFileInputStream(f),
                this.getDataOutputStream(), f.length(), true), f, compress);
    }

    public boolean copyTo(BaseFile f, boolean compress, boolean viaTemp) throws IOException {
        if (viaTemp) {
            BaseFile tmp = f.getTempFile();
            boolean done = copyTo(tmp, compress);
            if (done) {
                return tmp.renameTo(f);
            } else {
                tmp.delete();
                return false;
            }
        } else {
            return copyTo(f, compress);
        }
    }

    public boolean copyTo(BaseFile f, boolean compress) throws IOException {
        java.io.InputStream is = getDataInputStream();
        java.io.OutputStream os = new BufferedOutputStream(f.getDataOutputStream());
        if (compress) {
            os = new GZIPOutputStream(os);
        }
        boolean done = IOUtil.copyStream(is, os, this.length(), true);
        os.flush();
        os.close();
        return done;
    }

    private boolean copyFromFileViaTemp(File f, boolean compress) throws IOException {
        java.io.InputStream is = FileUtilCore.getBufferedFileInputStream(f);
        BaseFile temp = this.getTempFile();
        // make sure that if the process failed previously we try to remove the temp file again.
        temp.delete();
        long size = f.length();
        temp.mkParentDir();
        java.io.OutputStream os = temp.getDataOutputStream();
        if (compress) {
            os = new GZIPOutputStream(os);
        }
        if (IOUtil.copyStream(is, os, size)) {
            return ensureFilesSameSize(temp.renameTo(this), f, compress);
        } else {
            return false;
        }
    }

    public boolean copyFromUsingTemp(File f) throws IOException {
        return copyFrom(f, true, false);
    }

    public boolean copyFromUsingTemp(File f, boolean compress) throws IOException {
        return copyFrom(f, true, compress);
    }


    /**
     * Copy to a local file.
     *
     * @param f
     * @return
     * @throws IOException
     */
    public boolean copyToFile(File f, boolean compress) throws IOException {
        java.io.OutputStream os = FileUtilCore.getBufferedFileOutputStream(f);
        if (compress) {
            os = new GZIPOutputStream(os);
        }
        return ensureFilesSameSize(IOUtil.copyStream(getDataInputStream(), os, length()), f, compress);
    }

    public boolean copyToFile(File f) throws IOException {
        return copyToFile(f, false);
    }

    public abstract boolean delete() throws IOException;

    public boolean deleteContentOfDir(boolean recursive) throws IOException {
        return deleteContentOfDir(recursive, this);
    }

    public boolean deleteAndRecreateDir(boolean recursive) throws IOException {
        deleteContentOfDir(recursive);
        mkdir();
        return true;
    }

    private boolean deleteContentOfDir(boolean recursive, BaseFile dir) throws IOException {
        BaseFile bfs[] = dir.listFiles();
        if (bfs == null) {
            return true;
        }
        for (BaseFile child : bfs) {
            if (child.isDir()) {
                if (recursive) {
                    deleteContentOfDir(recursive, child);
                    child.delete();
                } else {
                    child.delete();
                }
            } else {
                child.delete();
            }
        }
        return true;
    }

    private boolean ensureFilesSameSize(boolean result, File f, boolean isCompressing) {
        if (isCompressing) {
            // we actually cant tell as the file sizes are going to be different.
            return true;
        }
        if (length() != f.length()) {
            Log.filesystem.error("File lengths are not the same %s != %s for %s", length(), f.length(), f);
            return false;
        }
        return result;
    }

    public abstract boolean exists();

    public abstract String getAbsolutePath();


    public abstract F getChild(String child);


    public abstract boolean mkParentDir();


    public final DataInputStream getDataInputStream() throws IOException {
        return new DataInputStream(getInputStream());
    }

    public final InputStream getInputStream() throws IOException {
        CompressionType ct = getCompressionType();
        return ct.getInputCompressed(getInputStreamRaw());
    }

    public abstract InputStream getInputStreamRaw() throws IOException;

    public abstract OutputStream getOutputStreamRaw() throws IOException;

    public final DataOutputStream getDataOutputStream() throws IOException {
        return new DataOutputStream(getOutputStream());
    }

    public final OutputStream getOutputStream() throws IOException {
        CompressionType ct = getCompressionType();
        return ct.getOutputStreamCompressed(getOutputStreamRaw());
    }

    public abstract DataOutputStream getDataOutputStreamAppend() throws IOException;

    public abstract F getPeer(String peer);

    public abstract F getParentAux(String part);

    public F getParent() {
        if (StringUtilCore.nullOrEmptyString(path)) {
            // cant go higher than root of the path.
            return null;
        }
        int index = path.lastIndexOf("/");
        if (index != -1) {
            String part = path.substring(0, index);
            return getParentAux(part);
        }
        return null;
    }


    public BaseLock getLock(String lockName, String id) {
        return new BaseLock(this, lockName, id);
    }


    /**
     * Get the file extension of the file
     *
     * @return
     */
    public String getExtension() {
        // hack getName may not be complete right part of the path, but good enough to try to get an extension if the right part is empty string if the
        // path is the complete file part.
        return FileUtilCore.getFileExtension(FmtCore.S("%s/%s", path, getName()));
    }

    public F getChild(String childMsg, Object... args) {
        return getChild(FmtCore.Sargs(childMsg, args));
    }

    /**
     * Get underlying File System Root.
     *
     * @return
     */
    public FS getFS() {
        return fs;
    }

    public void setFS(FS fs) {
        this.fs = fs;
    }

    public abstract CInputStream getCInputStream() throws IOException;


    public abstract long getModifiedTime();

    /**
     * Return just the name of the file without the path
     *
     * @return
     */
    public String getName() {
        return FileUtilCore.getFileName(getAbsolutePath());
    }


    public int getNameAsInt() {
        String s = getName();
        return StringUtilCore.getIntNumberFromText(s);
    }

    public abstract COutputStream getCOutputStream() throws IOException;

    public abstract COutputStream getCOutputStreamAppend() throws IOException;

    public String getRelativePath() {
        return path;
    }

    public abstract F getTempFile();

    public abstract boolean isDir();

    public boolean isFile() {
        return !isDir();
    }

    public abstract long length();

    public BaseFile[] listFiles() throws IOException {
        return listFiles(x -> true);
    }

    /**
     * List files matching a glob pattern (e.g., "*.json", "data-*.csv").
     * Uses JDK PathMatcher syntax matched against filenames.
     */
    public BaseFile[] listFiles(String globPattern) throws IOException {
        java.nio.file.PathMatcher matcher =
                java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        return listFiles(bf -> matcher.matches(java.nio.file.Paths.get(bf.getName())));
    }

    public abstract BaseFile[] listFiles(Predicate<BaseFile> filter) throws IOException;


    public boolean writeStringListToFile(List<String> list) throws IOException {
        PrintStream ps = getPrintStream();
        for (String s : list) {
            ConsoleCore.println(ps, s);
        }
        ps.flush();
        ps.close();
        return true;
    }

    public List<String> getLines() throws IOException {
        List<String> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(getReader())) {
            String line;
            while ((line = br.readLine()) != null) {
                list.add(line);
            }
        }
        return list;
    }

    // ******************** Internal Utility "stuff" **************************

    public abstract boolean mkdir();

    public abstract boolean renameTo(BaseFile replaceWithMe) throws IOException;

    public abstract boolean replace(BaseFile replaceWithMe) throws IOException;

    public abstract boolean setLastModified(long time) throws IOException;

    private boolean checkFile(boolean result, BaseFile f) {
        if (length() != f.length()) {
            Log.filesystem.error("File lengths not the same after copy %s!=%s for %s", length(), f.length(), f);
            return false;
        }
        return result;
    }

    public boolean isLocal() {
        return false;
    }

    public void writeMap(TLongLongHashMap map) throws IOException {
        TLongLongIterator iter = map.iterator();
        COutputStream os = this.getCOutputStream();
        os.writeInt(map.size());
        while (iter.hasNext()) {
            iter.advance();
            os.writeLong(iter.key());
            os.writeLong(iter.value());
        }
        os.close();
    }

    public long readLong() throws IOException {
        try (DataInputStream dis = this.getDataInputStream()) {
            return dis.readLong();
        }
    }

    public void writeLong(long l) throws IOException {
        try (DataOutputStream dos = this.getDataOutputStream()) {
            dos.writeLong(l);
        }
    }

    public TLongLongHashMap readTLongLong() throws IOException {
        CInputStream is = this.getCInputStream();
        int size = is.readInt();
        TLongLongHashMap map = new TLongLongHashMap(size);
        for (int i = 0; i < size; i++) {
            long key = is.readLong();
            long val = is.readLong();
            map.put(key, val);
        }
        return map;
    }

}
