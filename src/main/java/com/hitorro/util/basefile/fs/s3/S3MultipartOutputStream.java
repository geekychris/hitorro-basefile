/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes one S3 object through a bounded buffer. A write that fits in one
 * part is a single PutObject on close; anything larger becomes a multipart
 * upload, one part per full buffer, so memory stays at one part whatever the
 * object's size. A failed upload is aborted, so no half-written parts linger.
 *
 * <p>The object appears only when {@link #close()} succeeds, as with S3A.</p>
 */
final class S3MultipartOutputStream extends OutputStream {

    /** S3's minimum part size (every part but the last). */
    static final int MIN_PART_SIZE = 5 * 1024 * 1024;

    private final S3Client s3;
    private final String bucket;
    private final String key;
    private final byte[] buf;
    private int len;
    private String uploadId;
    private final List<CompletedPart> parts = new ArrayList<>();
    private boolean closed;

    S3MultipartOutputStream(S3Client s3, String bucket, String key, int partSize) {
        if (partSize < MIN_PART_SIZE) {
            throw new IllegalArgumentException("partSize must be at least " + MIN_PART_SIZE + " bytes");
        }
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
        this.buf = new byte[partSize];
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        if (len == buf.length) {
            flushPart();
        }
        buf[len++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int n) throws IOException {
        ensureOpen();
        while (n > 0) {
            if (len == buf.length) {
                flushPart();
            }
            int c = Math.min(n, buf.length - len);
            System.arraycopy(b, off, buf, len, c);
            len += c;
            off += c;
            n -= c;
        }
    }

    private void flushPart() throws IOException {
        try {
            if (uploadId == null) {
                uploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(bucket).key(key).build()).uploadId();
            }
            int number = parts.size() + 1;
            String etag = s3.uploadPart(UploadPartRequest.builder()
                            .bucket(bucket).key(key).uploadId(uploadId).partNumber(number).build(),
                    RequestBody.fromBytes(java.util.Arrays.copyOf(buf, len))).eTag();
            parts.add(CompletedPart.builder().partNumber(number).eTag(etag).build());
            len = 0;
        } catch (RuntimeException e) {
            abort();
            throw new IOException("uploading s3://" + bucket + "/" + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (uploadId == null) {
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        RequestBody.fromBytes(java.util.Arrays.copyOf(buf, len)));
                return;
            }
            if (len > 0) {
                flushPart();
            }
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
        } catch (RuntimeException e) {
            abort();
            throw new IOException("writing s3://" + bucket + "/" + key + ": " + e.getMessage(), e);
        }
    }

    /** How many parts were uploaded (0 for a single PutObject); for tests. */
    int partCount() {
        return parts.size();
    }

    private void abort() {
        if (uploadId == null) {
            return;
        }
        try {
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId).build());
        } catch (RuntimeException ignored) {
            // best effort: the bucket's lifecycle rule cleans up what this misses
        }
        uploadId = null;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
    }
}
