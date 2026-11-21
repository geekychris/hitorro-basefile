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
package com.hitorro.util.core.iterator.sinks;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import com.hitorro.util.json.JsonMappers;
import tools.jackson.core.JacksonException;

import com.hitorro.util.basefile.fs.BaseFile;
import com.hitorro.util.core.Constants;
import com.hitorro.util.core.HTRuntimeException;
import com.hitorro.util.core.Log;
import com.hitorro.util.core.string.StringUtil;

import java.io.*;


public class JsonSink extends BaseSink<JsonNode> {
    // Jackson 3 mappers are immutable, so the configurations the 3.x sink set on its own
    // mapper are built once here. Neither closes the target or flushes it on every write.
    private static final ObjectMapper JSON = JsonMappers.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
            .build();
    private static final ObjectMapper JSON_INDENT = JsonMappers.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    protected ObjectMapper mapper = JSON;
    protected boolean smile = false;
    protected OutputStream os;
    protected int count = 0;
    protected Writer ow;
    protected boolean flushOnAdd = false;
    protected String preRamble = null;
    protected String postRamble = null;
    protected boolean closed = false;
    protected boolean started = false;
    protected int logEveryN = 0;

    public JsonSink(OutputStream os, boolean setIndent, boolean flushOnAdd) {
        this(os, setIndent);
        this.flushOnAdd = flushOnAdd;
    }

    public JsonSink(BaseFile bf) throws IOException {
        this(bf.getDataOutputStream());
    }

    public JsonSink(OutputStream os) {
        this.os = os;
        if (os == null) {
            Log.util.error("Output Stream is null");
            return;
        }
        try {
            ow = new OutputStreamWriter(os, Constants.UTF8);
        } catch (UnsupportedEncodingException e) {
            Log.util.error("Unable to open outputStream");
            throw new HTRuntimeException("Unable to open output writer %s %e", e, e);
        }
    }

    /**
     * If we want to output in smile format (binary) do so with this option.
     *
     * @param os
     * @param indent
     * @param smileFormat
     */
    public JsonSink(OutputStream os, boolean indent, boolean smileFormat, boolean flushOnAdd) {
        this(os, indent);
        this.flushOnAdd = flushOnAdd;
        if (smileFormat) {
            // As in 3.x, the Smile mapper has Jackson's defaults (it closes the target) and
            // ignores indent.
            smile = true;
            mapper = JsonMappers.smile();
        }
    }

    public JsonSink(OutputStream os, boolean indent) {
        this(os);

        setIndent(indent);
    }

    public void setPrePostRamble(String pre, String post) {
        this.preRamble = pre;
        this.postRamble = post;
    }

    /**
     * Output a count of how many elements processed at every n elements
     *
     * @param n
     */
    public void logEveryNElements(int n) {
        logEveryN = n;
    }

    public void setIndent(boolean indent) {
        if (smile) {
            return; // pretty printing has no meaning for Smile
        }
        mapper = indent ? JSON_INDENT : JSON;
    }

    @Override
    public boolean init(JsonNode node) {
        return true;
    }

    public boolean start() {
        if (!started) {
            if (!StringUtil.nullOrEmptyString(preRamble)) {
                try {
                    ow.write(preRamble);
                    ow.flush();
                } catch (IOException e) {
                    throw new HTRuntimeException("Unable to write to output writer %s %e", e, e);
                }
            }
            started = true;
        }
        return started;
    }

    public void writeOutSeperator() throws IOException {
        ow.write('\n');
    }

    public boolean add(JsonNode o) {
        if (o == null) {
            return false;
        }
        try {
            if (count > 0) {
                writeOutSeperator();
            }
            mapper.writeValue(ow, o);
            count++;
            if (logEveryN > 0 && count % logEveryN == 0) {
                Log.util.info("Another %s docs %s", logEveryN, count);
            }

            if (flushOnAdd) {
                ow.flush();
            }
            return true;
        } catch (JacksonException | IOException e) {
            Log.util.error("unable to put in sink, %s %e", e, e);
            return false;
        }
    }

    public boolean stop() {
        if (started) {
            if (closed) {
                Log.util.error("Trying to end something that is closed %e", new Throwable());
            } else {
                try {
                    if (!StringUtil.nullOrEmptyString(postRamble)) {
                        ow.write(postRamble);
                    }

                    if (count > 0) {
                        ow.flush();
                    }
                } catch (IOException e) {
                    Log.util.error("unable to close stream in JsonSink, %s %e", e, e);
                }
            }
            started = false;
        }
        return !started;
    }


    public void close() {
        if (closed == false) {
            try {
                ow.close();
            } catch (IOException e) {
                Log.util.error("unable to close stream in JsonSink, %s", e);
            }
            closed = true;
        }
    }
}

