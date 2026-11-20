/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.BaseFileSystem;
import com.hitorro.util.basefile.fs.ProtocolAdapter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes {@code s3://bucket/key} paths to one S3-compatible endpoint (MinIO,
 * Ceph, R2…). Register it with {@link BaseFileSystem#addProtocolAdapter} and
 * every {@code BaseFileSystem.getBaseFileFromPath("s3://…")} lands there.
 *
 * <p>An empty bucket in the path ({@code s3:///key}) uses the configured
 * bucket. One {@link S3CompatibleFileSystem}, and so one S3 client, is kept
 * per bucket.</p>
 */
public final class MinioProtocolAdapter implements ProtocolAdapter<S3File> {

    /** Protocol prefix this adapter claims: {@code s3://…} URIs. */
    public static final String PROTOCOL = "s3";

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final boolean sslEnabled;
    private final ConcurrentHashMap<String, S3CompatibleFileSystem> byBucket = new ConcurrentHashMap<>();

    public MinioProtocolAdapter(String endpoint, String bucket,
                                String accessKey, String secretKey,
                                boolean sslEnabled) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("MinioProtocolAdapter requires an endpoint (e.g. http://minio:9000)");
        }
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.sslEnabled = sslEnabled;
    }

    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    @Override
    public BaseFile getBaseFileFromPath(String val) {
        S3Path p = S3Path.parse(val);
        String b = p.bucket().isEmpty() ? bucket : p.bucket();
        return fileSystem(b).getFile(p.key());
    }

    /** The file system for {@code bucket}, shared by every path in it. */
    public S3CompatibleFileSystem fileSystem(String bucket) {
        return byBucket.computeIfAbsent(bucket,
                b -> new S3CompatibleFileSystem(endpoint, b, accessKey, secretKey, sslEnabled));
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }
}
