/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import com.hitorro.util.basefile.fs.configfactories.FileSystemConfig;

/**
 * S3 bucket settings. With an {@link #endpoint} it is an S3-compatible store
 * (MinIO, Ceph, R2…); without one, AWS S3.
 */
public class S3Config extends FileSystemConfig<HTS3FileSystem> {
    /** S3 bucket name */
    public String bucket;

    /** Secret access key; null for the SDK's default credential chain */
    public String secretAccessKey;

    /** Access key ID; null for the SDK's default credential chain */
    public String accessKey;

    /** Region (e.g. "us-east-1"), optional */
    public String region;

    /** S3-compatible endpoint (e.g. "http://minio:9000"), optional */
    public String endpoint;

    public HTS3FileSystem getFileSystem() {
        if (endpoint != null && !endpoint.isBlank()) {
            return new S3CompatibleFileSystem(endpoint, bucket, accessKey, secretAccessKey, true, region);
        }
        return new HTS3FileSystem(this);
    }
}
