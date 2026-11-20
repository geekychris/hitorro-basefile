/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs;

import java.io.IOException;

/**
 * A file system that isn't there: every lookup returns null, as an HDFS file
 * system does when HDFS isn't enabled. {@link BaseFileSystem} uses it for the
 * global namespace when Hadoop is missing.
 */
public final class UnavailableFileSystem extends BaseFileSystem<BaseFile, UnavailableFileSystem> {

    private final String reason;

    public UnavailableFileSystem(String reason) {
        this.reason = reason;
        this.pathPart = "";
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean deleteFileSystem() throws IOException {
        throw new IOException(reason);
    }

    @Override
    public BaseFile getFile(BaseFile af) {
        return null;
    }

    @Override
    public BaseFile getFile(String path) {
        return null;
    }

    @Override
    public BaseFile getFileEnsuringDir(String path) {
        return null;
    }

    @Override
    public boolean isLocalFileSystem() {
        return false;
    }

    @Override
    public String toString() {
        return "unavailable file system: " + reason;
    }
}
