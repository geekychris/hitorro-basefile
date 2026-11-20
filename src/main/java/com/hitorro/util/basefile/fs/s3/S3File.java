/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.io.largedata.compressedstreams.CInputStream;
import com.hitorro.util.io.largedata.compressedstreams.COutputStream;
import com.hitorro.util.io.largedata.compressedstreams.InputInputStream;
import com.hitorro.util.io.largedata.compressedstreams.OutputOutputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One object (or key prefix) in an {@link HTS3FileSystem} bucket.
 *
 * <p>S3 is not a file system, and this behaves as S3A does: a "directory" is
 * a key prefix, present when anything is stored under it; {@link #mkdir()}
 * writes an empty {@code key/} marker; rename is copy then delete; appends and
 * timestamps can't be set. Metadata is read once and cached; a write or delete
 * through this object clears it.</p>
 */
public class S3File extends BaseFile<S3File, HTS3FileSystem> {

    private final HTS3FileSystem s3fs;
    /** The object key ("" is the root of the file system). */
    private final String key;
    private HeadObjectResponse head;
    private boolean headLoaded;

    S3File(HTS3FileSystem fs, String path) {
        this(fs, path, null);
    }

    private S3File(HTS3FileSystem fs, String path, HeadObjectResponse head) {
        setFS(fs);
        this.s3fs = fs;
        this.path = trim(path);
        String root = trim(fs.getPathPart());
        this.key = root.isEmpty() ? this.path : this.path.isEmpty() ? root : root + "/" + this.path;
        this.head = head;
        this.headLoaded = head != null;
    }

    private static String trim(String p) {
        if (p == null) {
            return "";
        }
        int s = 0, e = p.length();
        while (s < e && p.charAt(s) == '/') s++;
        while (e > s && p.charAt(e - 1) == '/') e--;
        return p.substring(s, e);
    }

    private S3Client s3() {
        return s3fs.getClient();
    }

    private String bucket() {
        return s3fs.getBucketName();
    }

    /** The object key in the bucket. */
    public String getKey() {
        return key;
    }

    private String dirPrefix() {
        return key.isEmpty() ? "" : key + "/";
    }

    private HeadObjectResponse head() {
        if (!headLoaded) {
            headLoaded = true;
            head = null;
            if (!key.isEmpty()) {
                try {
                    head = s3().headObject(HeadObjectRequest.builder().bucket(bucket()).key(key).build());
                } catch (S3Exception e) {
                    if (e.statusCode() != 404) {
                        throw e;
                    }
                }
            }
        }
        return head;
    }

    private void forget() {
        headLoaded = false;
        head = null;
    }

    /** Whether anything is stored under {@code key/}. */
    private boolean hasChildren() {
        ListObjectsV2Response r = s3().listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket()).prefix(dirPrefix()).maxKeys(1).build());
        return r.keyCount() != null && r.keyCount() > 0;
    }

    @Override
    public boolean exists() {
        return head() != null || isDir();
    }

    @Override
    public boolean isFile() {
        return head() != null && !isDir();
    }

    /**
     * A prefix with something under it (a {@code key/} marker counts). Checked
     * before the object itself: some stores (SeaweedFS) answer HEAD for a
     * directory as if it were an object.
     */
    @Override
    public boolean isDir() {
        return key.isEmpty() || hasChildren() || markerExists();
    }

    /** Whether the {@code key/} marker {@link #mkdir()} writes is there. */
    private boolean markerExists() {
        try {
            s3().headObject(HeadObjectRequest.builder().bucket(bucket()).key(dirPrefix()).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public long length() {
        HeadObjectResponse h = head();
        return h == null ? -1 : h.contentLength();
    }

    @Override
    public long getModifiedTime() {
        HeadObjectResponse h = head();
        return h == null || h.lastModified() == null ? -1 : h.lastModified().toEpochMilli();
    }

    @Override
    public String getAbsolutePath() {
        return "s3://" + bucket() + "/" + key;
    }

    @Override
    public String toString() {
        return getAbsolutePath();
    }

    @Override
    public InputStream getInputStreamRaw() throws IOException {
        try {
            return s3().getObject(GetObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (S3Exception e) {
            throw new IOException("reading " + getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public OutputStream getOutputStreamRaw() {
        forget();
        return new S3MultipartOutputStream(s3(), bucket(), key, s3fs.getPartSize());
    }

    /** S3 objects can't be appended to. */
    @Override
    public DataOutputStream getDataOutputStreamAppend() {
        throw new UnsupportedOperationException("S3 objects can't be appended to: " + getAbsolutePath());
    }

    @Override
    public CInputStream getCInputStream() throws IOException {
        return new InputInputStream(new java.io.DataInputStream(getInputStreamRaw()), length());
    }

    @Override
    public COutputStream getCOutputStream() throws IOException {
        return new OutputOutputStream(new DataOutputStream(getOutputStreamRaw()));
    }

    /** S3 objects can't be appended to. */
    @Override
    public COutputStream getCOutputStreamAppend() {
        throw new UnsupportedOperationException("S3 objects can't be appended to: " + getAbsolutePath());
    }

    /** Deletes the object, or everything under the prefix when it is a directory. */
    @Override
    public boolean delete() throws IOException {
        if (key.isEmpty()) {
            throw new IOException("refusing to delete every object in bucket " + bucket());
        }
        try {
            if (head() != null) {
                s3().deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
            }
            deletePrefix(dirPrefix());
            forget();
            return true;
        } catch (S3Exception e) {
            throw new IOException("deleting " + getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Deletes every object under {@code prefix}, then the {@code dir/} markers
     * of every level, deepest first: stores with real directories (SeaweedFS)
     * keep them otherwise. On AWS deleting a missing key does nothing.
     */
    private void deletePrefix(String prefix) {
        java.util.TreeSet<String> dirs = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
        dirs.add(prefix);
        String token = null;
        do {
            ListObjectsV2Response r = s3().listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket()).prefix(prefix).continuationToken(token).build());
            List<ObjectIdentifier> ids = new ArrayList<>();
            for (S3Object o : r.contents()) {
                ids.add(ObjectIdentifier.builder().key(o.key()).build());
                for (int i = o.key().indexOf('/', prefix.length()); i >= 0; i = o.key().indexOf('/', i + 1)) {
                    dirs.add(o.key().substring(0, i + 1));
                }
            }
            if (!ids.isEmpty()) {
                s3().deleteObjects(DeleteObjectsRequest.builder().bucket(bucket())
                        .delete(Delete.builder().objects(ids).quiet(true).build()).build());
            }
            token = Boolean.TRUE.equals(r.isTruncated()) ? r.nextContinuationToken() : null;
        } while (token != null);
        for (String d : dirs) {
            try {
                s3().deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(d).build());
            } catch (S3Exception e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
            }
        }
    }

    /** S3 keeps no access time; an existing object is left as is, a missing one is created empty. */
    @Override
    public void touch() {
        if (!exists()) {
            s3().putObject(PutObjectRequest.builder().bucket(bucket()).key(key).build(), RequestBody.empty());
            forget();
        }
    }

    /** S3 sets an object's time when it is written; it can't be changed. */
    @Override
    public boolean setLastModified(long time) {
        return false;
    }

    /** Writes an empty {@code key/} marker, as S3A does, so empty directories show up. */
    @Override
    public boolean mkdir() {
        if (key.isEmpty()) {
            return true;
        }
        s3().putObject(PutObjectRequest.builder().bucket(bucket()).key(dirPrefix()).build(), RequestBody.empty());
        return true;
    }

    /** Prefixes need no creating. */
    @Override
    public boolean mkParentDir() {
        return true;
    }

    @Override
    public S3File getChild(String child) {
        return new S3File(s3fs, path.isEmpty() ? child : path + "/" + child);
    }

    @Override
    public S3File getPeer(String peer) {
        int i = path.lastIndexOf('/');
        return new S3File(s3fs, i < 0 ? peer : path.substring(0, i) + "/" + peer);
    }

    @Override
    public S3File getParentAux(String part) {
        return new S3File(s3fs, part);
    }

    @Override
    public S3File getTempFile() {
        return new S3File(s3fs, path + ".tmp");
    }

    /**
     * The immediate children: objects directly under {@code key/} and the
     * prefixes one level down (as directories). Null when there are none,
     * like {@link java.io.File#listFiles()}.
     */
    @Override
    public S3File[] listFiles(Predicate<BaseFile> filter) throws IOException {
        List<S3File> out = new ArrayList<>();
        String prefix = dirPrefix();
        String token = null;
        try {
            do {
                ListObjectsV2Response r = s3().listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket()).prefix(prefix).delimiter("/").continuationToken(token).build());
                for (CommonPrefix p : r.commonPrefixes()) {
                    String name = trim(p.prefix().substring(prefix.length()));
                    add(out, filter, new S3File(s3fs, childPath(name)));
                }
                for (S3Object o : r.contents()) {
                    String name = o.key().substring(prefix.length());
                    if (name.isEmpty()) {
                        continue; // the directory's own marker
                    }
                    HeadObjectResponse h = HeadObjectResponse.builder()
                            .contentLength(o.size()).lastModified(o.lastModified()).eTag(o.eTag()).build();
                    add(out, filter, new S3File(s3fs, childPath(name), h));
                }
                token = Boolean.TRUE.equals(r.isTruncated()) ? r.nextContinuationToken() : null;
            } while (token != null);
        } catch (S3Exception e) {
            throw new IOException("listing " + getAbsolutePath() + ": " + e.getMessage(), e);
        }
        return out.isEmpty() ? null : out.toArray(new S3File[0]);
    }

    private String childPath(String name) {
        return path.isEmpty() ? name : path + "/" + name;
    }

    private static void add(List<S3File> out, Predicate<BaseFile> filter, S3File f) {
        if (filter == null || filter.test(f)) {
            out.add(f);
        }
    }

    /** Moves this object onto {@code replaceWithMe}: a copy, then a delete. */
    @Override
    public boolean renameTo(BaseFile replaceWithMe) throws IOException {
        replaceWithMe.delete();
        if (replaceWithMe instanceof S3File to) {
            copyObject(this, to);
            delete();
            return true;
        }
        return replaceWithMe.replace(this);
    }

    /** Replaces this object with {@code replaceWithMe}, which is moved here. */
    @Override
    public boolean replace(BaseFile replaceWithMe) throws IOException {
        if (replaceWithMe instanceof S3File from) {
            if (!from.exists()) {
                return false;
            }
            copyObject(from, this);
            from.delete();
            return true;
        }
        if (!replaceWithMe.exists()) {
            return false;
        }
        try (InputStream in = replaceWithMe.getInputStreamRaw(); OutputStream out = getOutputStreamRaw()) {
            in.transferTo(out);
        }
        replaceWithMe.delete();
        return true;
    }

    private static void copyObject(S3File from, S3File to) throws IOException {
        try {
            to.s3().copyObject(CopyObjectRequest.builder()
                    .sourceBucket(from.bucket()).sourceKey(from.key)
                    .destinationBucket(to.bucket()).destinationKey(to.key).build());
            to.forget();
        } catch (S3Exception e) {
            throw new IOException("copying " + from + " to " + to + ": " + e.getMessage(), e);
        }
    }
}
