/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs.s3;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.BaseFileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The S3 backend against a real S3-compatible store: SeaweedFS in a container
 * (MinIO's images are no longer published). Skipped when Docker isn't
 * available; on Rancher Desktop set
 * {@code DOCKER_HOST=unix://$HOME/.rd/docker.sock}.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3FileIntegrationTest {

    static final String BUCKET = "basefile-test";
    static final String KEY = "test-access";
    static final String SECRET = "test-secret";

    /** SeaweedFS with its S3 gateway and one identity holding KEY/SECRET. */
    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:3.99"))
            .withCopyToContainer(Transferable.of(
                    "{\"identities\":[{\"name\":\"test\",\"credentials\":[{\"accessKey\":\"" + KEY + "\",\"secretKey\":\""
                            + SECRET + "\"}],\"actions\":[\"Admin\",\"Read\",\"List\",\"Tagging\",\"Write\"]}]}"),
                    "/etc/seaweedfs/s3.json")
            .withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data", "-volume.max=8")
            .withExposedPorts(8333)
            .waitingFor(Wait.forListeningPorts(8333).withStartupTimeout(Duration.ofMinutes(2)));

    static S3CompatibleFileSystem fs;

    static String endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(8333);
    }

    /** A fresh directory per test, so tests don't see each other's objects. */
    S3File dir;

    @BeforeAll
    static void bucket() throws InterruptedException {
        fs = new S3CompatibleFileSystem(endpoint(), BUCKET, KEY, SECRET, false);
        // the S3 port opens before the store can take writes: retry for a while
        long deadline = System.currentTimeMillis() + 60_000;
        while (true) {
            try {
                fs.getClient().createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
                fs.getFile("ready-probe").writeString("ok");
                return;
            } catch (Exception e) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("S3 store never became ready", e);
                }
                Thread.sleep(500);
            }
        }
    }

    @AfterAll
    static void close() {
        if (fs != null) {
            fs.close();
        }
    }

    @BeforeEach
    void freshDir() {
        dir = fs.getFile("t-" + UUID.randomUUID());
    }

    @Test
    void writesReadsAndDescribesAnObject() throws Exception {
        S3File f = dir.getChild("hello.txt");
        assertThat(f.exists()).isFalse();
        assertThat(f.length()).isEqualTo(-1);

        f.writeString("hello s3");

        assertThat(f.exists()).isTrue();
        assertThat(f.isDir()).isFalse();
        assertThat(f.isFile()).isTrue();
        assertThat(f.readString()).isEqualTo("hello s3");
        assertThat(f.length()).isEqualTo(8);
        assertThat(f.getModifiedTime()).isGreaterThan(System.currentTimeMillis() - 600_000);
        assertThat(f.getAbsolutePath()).isEqualTo("s3://" + BUCKET + "/" + dir.getKey() + "/hello.txt");
        assertThat(f.getName()).isEqualTo("hello.txt");
    }

    @Test
    void compressesByExtension() throws Exception {
        S3File gz = dir.getChild("data.json.gz");
        S3File bz2 = dir.getChild("data.json.bz2");
        String text = "{\"a\":1},".repeat(1000);
        gz.writeString(text);
        bz2.writeString(text);

        assertThat(gz.readString()).isEqualTo(text);
        assertThat(bz2.readString()).isEqualTo(text);
        assertThat(gz.length()).isLessThan(text.length());
        try (InputStream raw = bz2.getInputStreamRaw()) {
            assertThat(new String(raw.readNBytes(3), StandardCharsets.US_ASCII)).isEqualTo("BZh");
        }
    }

    @Test
    void directoriesArePrefixes() throws Exception {
        dir.getChild("a.txt").writeString("a");
        dir.getChild("sub/b.txt").writeString("b");
        dir.getChild("sub/deeper/c.txt").writeString("c");
        S3File empty = dir.getChild("empty");
        empty.mkdir();

        assertThat(dir.isDir()).isTrue();
        assertThat(dir.exists()).isTrue();
        assertThat(dir.getChild("sub").isDir()).isTrue();
        assertThat(empty.isDir()).isTrue();
        assertThat(dir.getChild("nothing-here").exists()).isFalse();

        BaseFile[] top = dir.listFiles();
        assertThat(Arrays.stream(top).map(BaseFile::getName)).containsExactlyInAnyOrder("a.txt", "sub", "empty");
        BaseFile a = Arrays.stream(top).filter(b -> b.getName().equals("a.txt")).findFirst().orElseThrow();
        assertThat(a.length()).isEqualTo(1);
        assertThat(Arrays.stream(dir.getChild("sub").listFiles()).map(BaseFile::getName))
                .containsExactlyInAnyOrder("b.txt", "deeper");
        assertThat(Arrays.stream(dir.listFiles("*.txt")).map(BaseFile::getName)).containsExactly("a.txt");
        assertThat(empty.listFiles()).isNull();

        S3File c = dir.getChild("sub/deeper/c.txt");
        assertThat(c.getParent().getRelativePath()).isEqualTo(dir.getRelativePath() + "/sub/deeper");
        assertThat(c.getPeer("d.txt").getRelativePath()).isEqualTo(dir.getRelativePath() + "/sub/deeper/d.txt");
    }

    @Test
    void deletingADirectoryDeletesEverythingUnderIt() throws Exception {
        dir.getChild("sub/b.txt").writeString("b");
        dir.getChild("sub/deeper/c.txt").writeString("c");
        S3File keep = dir.getChild("keep.txt");
        keep.writeString("k");

        assertThat(dir.getChild("sub").delete()).isTrue();

        assertThat(dir.getChild("sub").exists()).isFalse();
        assertThat(dir.getChild("sub/deeper/c.txt").exists()).isFalse();
        assertThat(keep.exists()).isTrue();
    }

    @Test
    void refusesToEmptyTheBucket() {
        assertThatThrownBy(() -> fs.getFile("").delete()).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> fs.deleteFileSystem()).isInstanceOf(IOException.class);
    }

    @Test
    void renameIsCopyThenDelete() throws Exception {
        S3File from = dir.getChild("from.txt");
        S3File to = dir.getChild("to.txt");
        from.writeString("moved");
        to.writeString("old");

        assertThat(from.renameTo(to)).isTrue();

        assertThat(from.exists()).isFalse();
        assertThat(to.readString()).isEqualTo("moved");
    }

    @Test
    void largeWritesUploadInPartsAndReadBackWhole() throws Exception {
        HTS3FileSystem small = new S3CompatibleFileSystem(endpoint(), BUCKET, KEY, SECRET, false)
                .withPartSize(S3MultipartOutputStream.MIN_PART_SIZE);
        S3File big = small.getFile(dir.getKey() + "/big.bin");
        byte[] data = new byte[S3MultipartOutputStream.MIN_PART_SIZE * 2 + 12345];
        new Random(7).nextBytes(data);

        OutputStream out = big.getOutputStreamRaw();
        out.write(data, 0, 1000);
        out.write(data, 1000, data.length - 1000);
        out.close();
        assertThat(((S3MultipartOutputStream) out).partCount()).isEqualTo(3);

        assertThat(big.length()).isEqualTo(data.length);
        try (InputStream in = big.getInputStreamRaw()) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
        assertThat(fs.getClient().listMultipartUploads(ListMultipartUploadsRequest.builder().bucket(BUCKET).build())
                .uploads()).isEmpty();
        small.close();
    }

    @Test
    void appendsAreRefused() {
        S3File f = dir.getChild("x.txt");
        assertThatThrownBy(f::getDataOutputStreamAppend).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(f::getCOutputStreamAppend).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void touchCreatesAMissingObjectAndLeavesAnExistingOne() throws Exception {
        S3File f = dir.getChild("touched.txt");
        f.touch();
        assertThat(f.exists()).isTrue();
        assertThat(f.length()).isZero();
        f.writeString("content");
        f.touch();
        assertThat(f.readString()).isEqualTo("content");
    }

    @Test
    void anAdapterRoutesS3PathsToTheEndpoint() throws Exception {
        MinioProtocolAdapter adapter = new MinioProtocolAdapter(endpoint(), BUCKET, KEY, SECRET, false);
        BaseFileSystem.addProtocolAdapter(adapter);
        try {
            BaseFile f = BaseFileSystem.getBaseFileFromPath("s3://" + BUCKET + "/" + dir.getKey() + "/via-adapter.txt");
            f.writeString("routed");
            assertThat(dir.getChild("via-adapter.txt").readString()).isEqualTo("routed");
            // an empty bucket means the adapter's own
            assertThat(BaseFileSystem.getBaseFileFromPath("s3:///" + dir.getKey() + "/via-adapter.txt").readString())
                    .isEqualTo("routed");
            // one file system (and S3 client) per bucket
            assertThat(adapter.fileSystem(BUCKET)).isSameAs(adapter.fileSystem(BUCKET));
        } finally {
            BaseFileSystem.addProtocolAdapter(new S3PropertyFactory() {
                @Override
                public String getProtocol() {
                    return S3PropertyFactory.S3;
                }
            });
        }
    }

    @Test
    void theDefaultFactoryUsesTheConfiguredEndpoint() throws Exception {
        String old = System.getProperty(S3PropertyFactory.ENDPOINT_PROPERTY);
        System.setProperty(S3PropertyFactory.ENDPOINT_PROPERTY, endpoint());
        System.setProperty("aws.accessKeyId", KEY);
        System.setProperty("aws.secretAccessKey", SECRET);
        try {
            S3PropertyFactory factory = new S3PropertyFactory();
            BaseFile f = factory.getBaseFileFromPath("s3a://" + BUCKET + "/" + dir.getKey() + "/default.txt");
            f.writeString("default chain");
            assertThat(dir.getChild("default.txt").readString()).isEqualTo("default chain");
            assertThat(factory.fileSystem(BUCKET)).isInstanceOf(S3CompatibleFileSystem.class);
        } finally {
            if (old == null) System.clearProperty(S3PropertyFactory.ENDPOINT_PROPERTY);
            else System.setProperty(S3PropertyFactory.ENDPOINT_PROPERTY, old);
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
        }
    }
}
