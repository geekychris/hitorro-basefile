/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * A bucket on any S3-compatible store reached at its own endpoint: MinIO,
 * Ceph RGW, SeaweedFS, Cloudflare R2, Backblaze B2, Wasabi, Oracle OCI,
 * Google Cloud Storage's S3 API (HMAC keys)… Uses path-style access, which
 * every one of them accepts.
 *
 * <p>The endpoint may carry its scheme ({@code http://minio:9000}); without one,
 * {@code sslEnabled} picks https or http. The region defaults to
 * {@code us-east-1}, which S3-compatible stores accept for signing.</p>
 *
 * <p>Checksums are sent and checked only when an operation requires them. The
 * SDK's default since 2.30 (a CRC trailer on every upload, in aws-chunked
 * framing) isn't understood by every S3-compatible store: some store the
 * framing as part of the object. This is AWS's advice for third-party stores.</p>
 */
public class S3CompatibleFileSystem extends HTS3FileSystem {

    private final String endpoint;
    private final boolean sslEnabled;

    /**
     * @param endpoint        the store's endpoint, e.g. {@code http://localhost:9000}
     * @param bucketName      the bucket
     * @param accessKey       access key ID
     * @param secretAccessKey secret access key
     * @param sslEnabled      https when the endpoint has no scheme
     */
    public S3CompatibleFileSystem(String endpoint, String bucketName,
                                  String accessKey, String secretAccessKey,
                                  boolean sslEnabled) {
        this(endpoint, bucketName, accessKey, secretAccessKey, sslEnabled, null);
    }

    /** As above, with SSL on when the endpoint has no scheme. */
    public S3CompatibleFileSystem(String endpoint, String bucketName,
                                  String accessKey, String secretAccessKey) {
        this(endpoint, bucketName, accessKey, secretAccessKey, true);
    }

    /** As above, signing for {@code region} (null: us-east-1). */
    public S3CompatibleFileSystem(String endpoint, String bucketName,
                                  String accessKey, String secretAccessKey,
                                  boolean sslEnabled, String region) {
        super(bucketName, secretAccessKey, accessKey, region == null || region.isEmpty() ? "us-east-1" : region);
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("S3CompatibleFileSystem needs an endpoint, e.g. http://minio:9000");
        }
        this.endpoint = endpoint;
        this.sslEnabled = sslEnabled;
    }

    @Override
    protected void configureClient(S3ClientBuilder builder) {
        builder.endpointOverride(endpointUri()).forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (getRegion() != null) {
            builder.region(Region.of(getRegion()));
        }
    }

    /** The endpoint with a scheme: the given one, or https/http by {@link #isSslEnabled()}. */
    public URI endpointUri() {
        return URI.create(endpoint.contains("://") ? endpoint : (sslEnabled ? "https://" : "http://") + endpoint);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }
}
