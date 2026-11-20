/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

/**
 * A parsed {@code s3://bucket/key} (or {@code s3a://}, {@code s3n://}) path.
 * The key never starts or ends with "/"; both may be empty.
 */
public record S3Path(String bucket, String key) {

    public static S3Path parse(String val) {
        if (val == null) {
            throw new IllegalArgumentException("no S3 path");
        }
        int scheme = val.indexOf("://");
        String rest = scheme >= 0 ? val.substring(scheme + 3) : val;
        int slash = rest.indexOf('/');
        String bucket = slash < 0 ? rest : rest.substring(0, slash);
        String key = slash < 0 ? "" : rest.substring(slash + 1);
        while (key.startsWith("/")) key = key.substring(1);
        while (key.endsWith("/")) key = key.substring(0, key.length() - 1);
        return new S3Path(bucket, key);
    }

    @Override
    public String toString() {
        return "s3://" + bucket + "/" + key;
    }
}
