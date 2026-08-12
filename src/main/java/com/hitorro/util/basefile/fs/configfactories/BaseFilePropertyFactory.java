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
package com.hitorro.util.basefile.fs.configfactories;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.basefile.fs.BaseFileSystem;
import com.hitorro.util.basefile.fs.ProtocolAdapter;
import com.hitorro.util.core.string.FmtCore;
import com.hitorro.util.core.string.StringUtilCore;
import java.io.IOException;

/**
 * Handles constructing a file using the syntax:
 *
 * <protocol>://@@credskey@@/path
 * Where credskey is a the key "filesetmanagement.creds.<key>" that defines a connection in configs
 *
 * <protocol>://@@auth_parts@@/path
 * where auth_parts is a set of / delimited parts such as username, password
 */
public abstract class BaseFilePropertyFactory<C extends FileSystemConfig, F extends BaseFile>
        implements ProtocolAdapter<F> {

    public BaseFile getBaseFileFromPath(String val) throws IOException {
        String parts[] = StringUtilCore.tokenizeFromMultiChar(val, "@@", false);
        if (parts == null || parts.length < 2) {
            return null;
        }
        String connect[] = StringUtilCore.tokenizeFromSingleChar(parts[1], "/");
        if (connect == null) {
            return null;
        }
        FileSystemConfig conf;
        if (connect.length != 1) {
            // we have a connection inline
            conf = getConfigFromParts(connect);
        } else {
            throw new IOException(FmtCore.S("Unable to get file system config for %s - ComplexPropertyContext not available in hitorro-core", connect[0]));
        }

        BaseFileSystem prov = conf.getFileSystem();
        if (parts.length == 3) {
            return prov.getFile(parts[2]);
        }
        if (parts.length == 2) {
            return prov.getFile("");
        }
        return null;
    }

    public abstract FileSystemConfig getConfigFromParts(String parts[]);
}
