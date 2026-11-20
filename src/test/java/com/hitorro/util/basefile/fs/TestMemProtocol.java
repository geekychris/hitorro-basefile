/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.basefile.fs;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A ProtocolAdapter registered through META-INF/services, for ProtocolRegistryTest. */
public class TestMemProtocol implements ProtocolAdapter<BaseFile> {
    static final String PROTOCOL = "mem";
    static final List<String> asked = new CopyOnWriteArrayList<>();

    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    @Override
    public BaseFile getBaseFileFromPath(String val) {
        asked.add(val);
        return null;
    }
}
