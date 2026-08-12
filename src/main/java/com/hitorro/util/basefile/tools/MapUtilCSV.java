/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.util.basefile.tools;

import com.hitorro.util.core.string.StringUtilCore;
import com.hitorro.util.io.csv.CSVReader;
import com.hitorro.util.io.csv.ColumnTableMeta;
import com.hitorro.util.io.csv.csvconsumer.CSVConsumer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * CSV-loading helpers previously on {@link com.hitorro.util.core.map.MapUtil}.
 * Extracted so MapUtil (in hitorro-streams) does not need to import CSVReader,
 * which pulled BaseFile transitively into streams via its BaseFile ctors.
 */
public final class MapUtilCSV {
    private MapUtilCSV() {}

    public static Map<String, String> getMapFromKeyValueCSV(File file, final String keyName, final String valueName) throws IOException {
        final Map<String, String> map = new HashMap<>();
        CSVReader csr = new CSVReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        CSVConsumer consumer = new CSVConsumer() {
            int keyOrd;
            int valueOrd;

            @Override
            public void line(int rowCount, String[] row) {
                if (rowCount == 0) {
                    ColumnTableMeta meta = ColumnTableMeta.init(row);
                    keyOrd = meta.getColumnInt(keyName);
                    valueOrd = meta.getColumnInt(valueName);
                } else {
                    String k = row[keyOrd];
                    String v = row[valueOrd];
                    if (StringUtilCore.nullOrEmptyString(k)) {
                        return;
                    }
                    map.put(k, v);
                }
            }
        };
        csr.readLines(consumer);
        return map;
    }
}
