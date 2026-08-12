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
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.util.core.iterator.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.core.iterator.AbstractIterator;
import com.hitorro.util.core.iterator.ArrayIterator;
import com.hitorro.util.core.iterator.JSONIterator;
import com.hitorro.util.core.iterator.LineReaderIterator;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Iterator-returning helpers over {@link BaseFile}. These moved off {@code BaseFile} itself
 * so that {@code BaseFile} (which lives in the low-level {@code util.basefile.fs} package)
 * has no compile-time dependency on the iterator framework. Callers previously doing
 * {@code bf.list()} now write {@code BaseFileIterators.list(bf)}.
 */
public final class BaseFileIterators {

    private BaseFileIterators() {}

    public static AbstractIterator<JsonNode> getJsonIterator(BaseFile bf) throws IOException {
        return getJsonIterator(bf, null);
    }

    public static AbstractIterator<JsonNode> getJsonIterator(BaseFile bf, String typeName) throws IOException {
        return new JSONIterator(bf.getReader(), typeName);
    }

    public static AbstractIterator<BaseFile> list(BaseFile bf) throws IOException {
        return new ArrayIterator<>(bf.listFiles());
    }

    public static AbstractIterator<BaseFile> list(BaseFile bf, Predicate<BaseFile> filter) throws IOException {
        return new ArrayIterator<>(bf.listFiles(filter));
    }

    public static AbstractIterator<String> getLineReaderIteratorFromFile(BaseFile bf) throws IOException {
        return new LineReaderIterator(bf.getReader());
    }

    /**
     * Iterator over the immediate directory entries of {@code bf}. Returns null if the
     * underlying list is null. Preserves the raw-typed return of the original {@code dirIterator()}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static AbstractIterator dirIterator(BaseFile bf) throws IOException {
        BaseFile[] l = bf.listFiles();
        if (l == null) return null;
        return new ArrayIterator(l);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static AbstractIterator dirIterator(BaseFile bf, Predicate<BaseFile> filter) throws IOException {
        BaseFile[] l = bf.listFiles(filter);
        if (l == null) return null;
        return new ArrayIterator(l);
    }
}
