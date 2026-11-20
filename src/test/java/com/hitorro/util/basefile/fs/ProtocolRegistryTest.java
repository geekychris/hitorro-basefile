/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs;

import com.hitorro.util.basefile.fs.s3.S3File;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolRegistryTest {

    @Test
    void registersTheBuiltInProtocols() {
        assertThat(BaseFileSystem.getProtocols()).contains("s3", "s3a", "ftp", "file");
        // Hadoop is an optional dependency, so it is on this module's own test classpath
        assertThat(BaseFileSystem.HADOOP_AVAILABLE).isTrue();
        assertThat(BaseFileSystem.getProtocols()).contains("hdfs");
    }

    @Test
    void s3PathsResolveToTheSdkBackend() throws IOException {
        assertThat(BaseFileSystem.getProtocolAdapter("s3://bucket/key").getBaseFileFromPath("s3://bucket/key"))
                .isInstanceOf(S3File.class);
        assertThat(BaseFileSystem.getProtocolAdapter("s3a://bucket/key").getBaseFileFromPath("s3a://bucket/key"))
                .isInstanceOf(S3File.class);
    }

    @Test
    void anAdapterOnTheClasspathPlugsIn() throws IOException {
        // src/test/resources/META-INF/services/com.hitorro.util.basefile.fs.ProtocolAdapter
        assertThat(BaseFileSystem.getProtocols()).contains(TestMemProtocol.PROTOCOL);
        assertThat(BaseFileSystem.getBaseFileFromPath("mem://anything")).isNull();
        assertThat(TestMemProtocol.asked).contains("mem://anything");
    }

    /**
     * Loads BaseFileSystem in a class loader that hides Hadoop, as in an app
     * that doesn't add hadoop-client: it must start, leave hdfs:// out, and
     * still resolve s3:// and local files.
     */
    @Test
    void worksWithoutHadoop() throws Exception {
        ClassLoader noHadoop = new HidingClassLoader(getClass().getClassLoader());
        Class<?> bfs = noHadoop.loadClass(BaseFileSystem.class.getName());
        assertThat(bfs.getClassLoader()).isSameAs(noHadoop);
        assertThat(bfs.getField("HADOOP_AVAILABLE").getBoolean(null)).isFalse();
        @SuppressWarnings("unchecked")
        Set<String> protocols = (Set<String>) bfs.getMethod("getProtocols").invoke(null);
        assertThat(protocols).contains("s3", "s3a", "file").doesNotContain("hdfs");

        Method get = bfs.getMethod("getBaseFileFromPath", String.class);
        Object s3 = get.invoke(null, "s3://bucket/key.txt");
        assertThat(s3.getClass().getName()).isEqualTo(S3File.class.getName());
        Object global = bfs.getMethod("getGlobalNamespaceFileSystem", String.class).invoke(null, "x");
        assertThat(global.getClass().getSimpleName()).isEqualTo("UnavailableFileSystem");

        Class<?> ct = noHadoop.loadClass(CompressionType.class.getName());
        Object bz2 = ct.getMethod("valueOf", String.class).invoke(null, "bz2");
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (var out = (java.io.OutputStream) ct.getMethod("getOutputStreamCompressed", java.io.OutputStream.class).invoke(bz2, sink)) {
            out.write("no hadoop needed".getBytes());
        }
        assertThat(sink.toByteArray()).startsWith('B', 'Z', 'h');
    }

    /** Child-first for com.hitorro classes; Hadoop isn't there. */
    static final class HidingClassLoader extends ClassLoader {
        HidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.apache.hadoop.")) {
                throw new ClassNotFoundException(name + " (hidden)");
            }
            if (!name.startsWith("com.hitorro.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    String res = name.replace('.', '/') + ".class";
                    try (InputStream in = getParent().getResourceAsStream(res)) {
                        if (in == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] b = in.readAllBytes();
                        c = defineClass(name, b, 0, b.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }
}
