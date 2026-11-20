/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class S3PathTest {

    @Test
    void splitsBucketAndKeyForEveryScheme() {
        for (String scheme : new String[]{"s3", "s3a", "s3n"}) {
            S3Path p = S3Path.parse(scheme + "://bucket/a/b/c.json");
            assertThat(p.bucket()).isEqualTo("bucket");
            assertThat(p.key()).isEqualTo("a/b/c.json");
        }
    }

    @Test
    void trimsSlashesAndAllowsAnEmptyKeyOrBucket() {
        assertThat(S3Path.parse("s3://bucket")).isEqualTo(new S3Path("bucket", ""));
        assertThat(S3Path.parse("s3://bucket/")).isEqualTo(new S3Path("bucket", ""));
        assertThat(S3Path.parse("s3://bucket//dir/")).isEqualTo(new S3Path("bucket", "dir"));
        assertThat(S3Path.parse("s3:///key/x")).isEqualTo(new S3Path("", "key/x"));
        assertThat(S3Path.parse("bucket/key")).isEqualTo(new S3Path("bucket", "key"));
    }

    @Test
    void anEndpointWithoutASchemeFollowsSsl() {
        assertThat(new S3CompatibleFileSystem("minio:9000", "b", "a", "s", false).endpointUri().toString())
                .isEqualTo("http://minio:9000");
        assertThat(new S3CompatibleFileSystem("minio:9000", "b", "a", "s", true).endpointUri().toString())
                .isEqualTo("https://minio:9000");
        assertThat(new S3CompatibleFileSystem("http://minio:9000", "b", "a", "s", true).endpointUri().toString())
                .isEqualTo("http://minio:9000");
        assertThat(new S3CompatibleFileSystem("http://minio:9000", "b", "a", "s").getRegion()).isEqualTo("us-east-1");
    }

    @Test
    void configWithAnEndpointIsS3Compatible() {
        S3Config c = new S3Config();
        c.bucket = "b";
        assertThat(c.getFileSystem()).isExactlyInstanceOf(HTS3FileSystem.class);
        c.endpoint = "http://minio:9000";
        assertThat(c.getFileSystem()).isInstanceOf(S3CompatibleFileSystem.class);
    }

    @Test
    void filePathsAreKeys() {
        HTS3FileSystem fs = new HTS3FileSystem("b", null, null, "us-east-1");
        S3File f = fs.getFile("/dir/sub/file.txt");
        assertThat(f.getKey()).isEqualTo("dir/sub/file.txt");
        assertThat(f.getAbsolutePath()).isEqualTo("s3://b/dir/sub/file.txt");
        assertThat(f.getChild("x").getKey()).isEqualTo("dir/sub/file.txt/x");
        assertThat(f.getPeer("y.txt").getKey()).isEqualTo("dir/sub/y.txt");
        assertThat(f.getTempFile().getKey()).isEqualTo("dir/sub/file.txt.tmp");
        assertThat(fs.getFile("").getChild("top").getKey()).isEqualTo("top");
        assertThat(fs.isLocalFileSystem()).isFalse();
    }
}
