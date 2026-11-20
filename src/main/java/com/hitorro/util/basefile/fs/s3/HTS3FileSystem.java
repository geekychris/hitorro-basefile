/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.BaseFileSystem;
import com.hitorro.util.core.Log;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.io.IOException;

/**
 * One S3 bucket as a {@link BaseFileSystem}, on the AWS SDK for Java v2 (no
 * Hadoop). Paths are object keys; "directories" are key prefixes, as in S3A.
 *
 * <p>Credentials are the explicit access/secret keys when given, otherwise the
 * SDK's default chain (environment, system properties, profile, container or
 * instance role). The region is the one given, otherwise the SDK's default
 * provider chain ({@code AWS_REGION}, profile…). For other S3-compatible stores
 * (MinIO, Ceph, R2, GCS's S3 API…) use {@link S3CompatibleFileSystem}, or
 * override {@link #configureClient(S3ClientBuilder)}.</p>
 *
 * <p>The S3 client is created on first use and shared by every file of this
 * file system; {@link #close()} releases it.</p>
 */
public class HTS3FileSystem extends BaseFileSystem<S3File, HTS3FileSystem> implements AutoCloseable {

    /** Default multipart part size: objects above it upload in parts of this size. */
    public static final int DEFAULT_PART_SIZE = 16 * 1024 * 1024;

    protected String bucketName;
    protected String secretAccessKey;
    protected String accessKey;
    private final String region;
    private int partSize = DEFAULT_PART_SIZE;
    private volatile S3Client client;

    /**
     * Create S3 filesystem with explicit credentials (null for the default chain).
     *
     * @param bucketName      S3 bucket name
     * @param secretAccessKey AWS secret access key
     * @param accessKey       AWS access key ID
     */
    public HTS3FileSystem(String bucketName, String secretAccessKey, String accessKey) {
        this(bucketName, secretAccessKey, accessKey, null);
    }

    /**
     * Create S3 filesystem with explicit credentials and region.
     *
     * @param bucketName      S3 bucket name
     * @param secretAccessKey AWS secret access key, or null for the default chain
     * @param accessKey       AWS access key ID, or null for the default chain
     * @param region          AWS region (e.g. "us-east-1"), or null for the default
     */
    public HTS3FileSystem(String bucketName, String secretAccessKey, String accessKey, String region) {
        this.bucketName = bucketName;
        this.secretAccessKey = secretAccessKey;
        this.accessKey = accessKey;
        this.region = region;
        this.pathPart = "";
    }

    public HTS3FileSystem(S3Config config) {
        this(config.bucket, config.secretAccessKey, config.accessKey, config.region);
    }

    /** The shared S3 client, created on first use. */
    public S3Client getClient() {
        S3Client c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    S3ClientBuilder b = S3Client.builder().credentialsProvider(credentials());
                    if (region != null && !region.isEmpty()) {
                        b.region(Region.of(region));
                    }
                    configureClient(b);
                    client = c = b.build();
                    Log.util.debug("S3 client ready for bucket %s", bucketName);
                }
            }
        }
        return c;
    }

    /**
     * Customize the S3 client before it is built: endpoint, path-style access,
     * HTTP client, retries. The default does nothing.
     */
    protected void configureClient(S3ClientBuilder builder) {
    }

    protected AwsCredentialsProvider credentials() {
        if (accessKey != null && secretAccessKey != null) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretAccessKey));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    /** Multipart part size for writes (at least 5 MiB). */
    public HTS3FileSystem withPartSize(int bytes) {
        if (bytes < S3MultipartOutputStream.MIN_PART_SIZE) {
            throw new IllegalArgumentException("part size must be at least 5 MiB");
        }
        this.partSize = bytes;
        return this;
    }

    public int getPartSize() {
        return partSize;
    }

    @Override
    public S3File getFile(BaseFile af) {
        return new S3File(this, af.getRelativePath());
    }

    @Override
    public S3File getFile(String path) {
        return new S3File(this, path);
    }

    @Override
    public S3File getFileEnsuringDir(String path) {
        return getFile(path);
    }

    /**
     * Deletes every object under this file system's root. Refuses when the root
     * is the whole bucket: emptying a bucket is not a file-system operation.
     */
    @Override
    public boolean deleteFileSystem() throws IOException {
        if (pathPart == null || pathPart.isEmpty()) {
            throw new IOException("refusing to delete every object in bucket " + bucketName);
        }
        return getFile("").delete();
    }

    @Override
    public boolean isLocalFileSystem() {
        return false;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getRegion() {
        return region;
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @Override
    public String toString() {
        return "s3://" + bucketName + (pathPart == null || pathPart.isEmpty() ? "" : "/" + pathPart);
    }
}
