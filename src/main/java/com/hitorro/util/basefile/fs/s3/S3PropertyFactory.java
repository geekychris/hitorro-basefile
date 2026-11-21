/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import tools.jackson.databind.JsonNode;
import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.configfactories.BaseFilePropertyFactory;
import com.hitorro.util.basefile.fs.configfactories.FileSystemConfig;
import com.hitorro.util.json.keys.StringProperty;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default handler for {@code s3://} and {@code s3a://} paths (both mean
 * the same thing; s3a is kept for paths written for Hadoop's S3A).
 *
 * <ul>
 *   <li>{@code s3://bucket/key}: AWS S3 with the SDK's default credentials and
 *       region. To reach another S3-compatible store instead, set its endpoint
 *       with the system property {@code hitorro.s3.endpoint} or the environment
 *       variable {@code HITORRO_S3_ENDPOINT} (credentials then come from the
 *       usual {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY}). Or
 *       register a {@link MinioProtocolAdapter}, which takes over {@code s3://}.</li>
 *   <li>{@code s3://@@bucket/secretAccessKey/accessKey[/region]@@path}: inline
 *       credentials (the BaseFile config syntax).</li>
 * </ul>
 * One file system, and so one S3 client, is kept per bucket.
 */
public class S3PropertyFactory extends BaseFilePropertyFactory<S3Config, S3File> {

    public static final String S3 = "s3";
    public static final String S3A = "s3a";

    /** System property / environment variable naming an S3-compatible endpoint. */
    public static final String ENDPOINT_PROPERTY = "hitorro.s3.endpoint";
    public static final String ENDPOINT_ENV = "HITORRO_S3_ENDPOINT";

    public static StringProperty BucketKey = new StringProperty("bucketname", "bucket name", null);
    public static StringProperty SecretKey = new StringProperty("secretaccesskey", "secret access key", null);
    public static StringProperty AccessKey = new StringProperty("accesskey", "access key", null);
    public static StringProperty RegionKey = new StringProperty("region", "AWS region", null);
    public static StringProperty EndpointKey = new StringProperty("endpoint", "S3-compatible endpoint", null);

    private final ConcurrentHashMap<String, HTS3FileSystem> byBucket = new ConcurrentHashMap<>();

    public String[] getNames() {
        return new String[]{"s3config"};
    }

    public S3Config getInstance(final JsonNode map, final String type, final String parentPathName) {
        S3Config s3 = new S3Config();
        s3.bucket = BucketKey.apply(map);
        s3.secretAccessKey = SecretKey.apply(map);
        s3.accessKey = AccessKey.apply(map);
        s3.region = RegionKey.apply(map);
        s3.endpoint = EndpointKey.apply(map);
        return s3;
    }

    /** s3a; {@code s3} is registered as an alias. */
    public String getProtocol() {
        return S3A;
    }

    @Override
    public BaseFile getBaseFileFromPath(String val) throws IOException {
        if (val.contains("@@")) {
            return super.getBaseFileFromPath(val);
        }
        S3Path p = S3Path.parse(val);
        if (p.bucket().isEmpty()) {
            return null;
        }
        return fileSystem(p.bucket()).getFile(p.key());
    }

    /** The default file system for {@code bucket}: AWS, or the configured endpoint. */
    public HTS3FileSystem fileSystem(String bucket) {
        return byBucket.computeIfAbsent(bucket, b -> {
            String endpoint = configuredEndpoint();
            return endpoint == null ? new HTS3FileSystem(b, null, null)
                    : new S3CompatibleFileSystem(endpoint, b, null, null, true);
        });
    }

    static String configuredEndpoint() {
        String e = System.getProperty(ENDPOINT_PROPERTY);
        if (e == null || e.isBlank()) {
            e = System.getenv(ENDPOINT_ENV);
        }
        return e == null || e.isBlank() ? null : e.trim();
    }

    /**
     * Inline config: {@code bucket/secretAccessKey/accessKey[/region]}.
     */
    public FileSystemConfig getConfigFromParts(String parts[]) {
        S3Config s3Config = new S3Config();
        s3Config.bucket = parts[0];
        s3Config.secretAccessKey = parts[1];
        s3Config.accessKey = parts[2];
        if (parts.length >= 4) {
            s3Config.region = parts[3];
        }
        return s3Config;
    }
}
