/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.BaseFileSystem;
import com.hitorro.util.basefile.fs.ProtocolAdapter;
import com.hitorro.util.basefile.fs.dfs.DFSFile;

import java.io.IOException;

/**
 * Runtime-installable {@link ProtocolAdapter} that routes {@code s3://…}
 * URIs to a {@link S3CompatibleFileSystem} configured for MinIO (or any
 * other S3-compatible service that uses a custom endpoint — Wasabi,
 * DigitalOcean Spaces, Backblaze B2, Cloudflare R2).
 *
 * <p>Install once at process startup (typically from a Spring
 * {@code @Configuration} bean that reads
 * {@code hitorro.storage.s3.*} properties):</p>
 *
 * <pre>
 * BaseFileSystem.addProtocolAdapter(new MinioProtocolAdapter(
 *         "http://minio:9000",
 *         "hitorro",
 *         "hitorro",
 *         "hitorro-dev-only",
 *         false));   // sslEnabled
 * </pre>
 *
 * <p>After registration, any {@code BaseFile bf = BaseFileSystem
 * .getBaseFileFromPath("s3://bucket/key/foo.ndjson.bz2")} routes
 * through the MinIO-backed filesystem — every sink, source, dataset
 * install script, and mesh agent can read/write S3 URIs transparently.</p>
 *
 * <p>The bundled default adapter registered by {@link BaseFileSystem}'s
 * static initializer targets AWS S3 with credentials from
 * {@code ~/.aws/credentials}. This adapter <b>replaces</b> that default
 * once installed — call {@link BaseFileSystem#addProtocolAdapter} again
 * with an AWS-specific instance if you need to swap back per-JVM.</p>
 */
public final class MinioProtocolAdapter implements ProtocolAdapter<DFSFile> {

    /** Protocol prefix this adapter claims — {@code s3://…} URIs. */
    public static final String PROTOCOL = "s3";

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final boolean sslEnabled;

    public MinioProtocolAdapter(String endpoint, String bucket,
                                String accessKey, String secretKey,
                                boolean sslEnabled) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("MinioProtocolAdapter requires an endpoint (e.g. http://minio:9000)");
        }
        this.endpoint   = endpoint;
        this.bucket     = bucket;
        this.accessKey  = accessKey;
        this.secretKey  = secretKey;
        this.sslEnabled = sslEnabled;
    }

    @Override public String getProtocol() { return PROTOCOL; }

    @Override
    public BaseFile getBaseFileFromPath(String val) throws IOException {
        // val looks like "s3://<bucket>/<path>" — split off the bucket, use
        // the rest as the path within that bucket. Falls through to the
        // adapter's configured bucket when the URL bucket matches.
        String stripped = val.startsWith("s3://") ? val.substring(5) : val;
        int slash = stripped.indexOf('/');
        String urlBucket = slash < 0 ? stripped : stripped.substring(0, slash);
        String pathInBucket = slash < 0 ? "" : stripped.substring(slash + 1);

        S3CompatibleFileSystem fs = new S3CompatibleFileSystem(
                endpoint,
                urlBucket.isEmpty() ? bucket : urlBucket,
                accessKey, secretKey, sslEnabled);
        return fs.getFile(pathInBucket);
    }

    public String getEndpoint()   { return endpoint; }
    public String getBucket()     { return bucket; }
    public String getAccessKey()  { return accessKey; }
    public boolean isSslEnabled() { return sslEnabled; }
}
