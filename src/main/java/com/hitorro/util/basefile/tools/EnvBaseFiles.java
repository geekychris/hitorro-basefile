/*
 * Copyright (c) 2006-2025 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */
package com.hitorro.util.basefile.tools;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.file.FileFileSystem;
import com.hitorro.util.core.EnvCore;

import java.io.File;

/**
 * BaseFile-returning helpers that used to live on {@link Env}. Extracted so that
 * {@code Env} (in hitorro-streams) does not have to depend on hitorro-basefile —
 * the dependency direction is basefile → streams, not the other way.
 */
public final class EnvBaseFiles {
    private EnvBaseFiles() {}

    private static FileFileSystem binFileSystem;
    private static FileFileSystem homeFileSystem;

    public static BaseFile getBaseFile(File file) {
        return FileFileSystem.Root.getFile(file.getAbsolutePath());
    }

    public static FileFileSystem getBinBaseFileSystem() {
        if (binFileSystem == null) {
            binFileSystem = new FileFileSystem(EnvCore.getBin());
        }
        return binFileSystem;
    }

    public static BaseFile getBinBaseFile() {
        return getBinBaseFileSystem().getFile("");
    }

    public static BaseFile getBinConfigBaseFile() {
        return getBinBaseFileSystem().getFile("config");
    }

    public static BaseFile getDataBaseFile() {
        return getBinBaseFileSystem().getFile("data");
    }

    public static BaseFile getWordnetDirectory() {
        return getDataBaseFile().getChild("WordNet-3.0");
    }

    public static FileFileSystem getHomeBaseFileSystem() {
        if (homeFileSystem == null) {
            homeFileSystem = new FileFileSystem(EnvCore.getHome());
        }
        return homeFileSystem;
    }

    public static BaseFile getHomeBaseFile() {
        return getHomeBaseFileSystem().getFile("");
    }

    public static BaseFile getHomeConfigBaseFile() {
        return getHomeBaseFileSystem().getFile("config");
    }
}
