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
package com.hitorro.util.basefile;

import tools.jackson.databind.*;
import com.hitorro.util.basefile.fs.*;
import com.hitorro.util.core.iterator.sinks.*;
import com.hitorro.util.core.iterator.json.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what basefile writes and reads to what 3.x (Jackson 2) did: JsonSink and
 * JSONSinkAsArray output (compact, indented, rambles, Smile, I/O errors), BaseFile
 * writeJson/readJsonElement/getJsonNode (plain and gzip), BaseJsonIterator over valid,
 * multi-document and invalid input, and Name2JsonMapper.
 *
 * <p>{@code jackson2-basefile.txt} is the output of the same code run on hitorro-basefile
 * 3.1.0 with Jackson 2.18 (generator: ~/hitorro-j3/tools/Jackson2BasefileGolden.java.txt).
 * Node and exception class names are compared after mapping the Jackson 3 names back
 * (StringNode = TextNode, StreamReadException = JsonParseException).
 */
class Jackson2FormatGoldenTest {
  private static final Map<String, String> out = new LinkedHashMap<>();
  static void p(String k, Object v){ out.put(k, String.valueOf(v).replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r")); }
  static String tryIt(java.util.concurrent.Callable<Object> c){ try { return String.valueOf(c.call()); } catch(Throwable t){ return "!"+t.getClass().getSimpleName(); } }
  static String hex(byte[] b){ StringBuilder s=new StringBuilder(); for(byte x:b) s.append(String.format("%02x",x)); return s.toString(); }
  static String types(JsonNode n){ StringBuilder t=new StringBuilder(); if (n == null || !n.isObject()) return ""; Iterator<String> it=n.propertyNames().iterator(); while(it.hasNext()){String k=it.next(); t.append(k+":"+n.get(k).getClass().getSimpleName()+",");} return t.toString(); }
  static Map<String, String> compute(Path dir) throws Exception {
    out.clear();
    ObjectMapper om = com.hitorro.util.json.JsonMappers.json();
    String doc1 = "{\"id\":1,\"name\":\"a\",\"tags\":[\"x\",\"y\"],\"meta\":{},\"u\":\"\u00e9\\u2028/\\t\",\"z\":-0.0}";
    String doc2 = "{\"id\":2,\"v\":1.10,\"n\":null,\"big\":12345678901234567890,\"dec\":123456789012345678901234567890.5,\"e\":[],\"b\":true,\"l\":12345678901}";
    JsonNode r1 = om.readTree(doc1), r2 = om.readTree(doc2);
    com.hitorro.util.basefile.fs.file.FileFileSystem fs = new com.hitorro.util.basefile.fs.file.FileFileSystem(dir.toFile());
    // JsonSink variants
    for (int v = 0; v < 6; v++) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(); JsonSink s;
      switch (v) {
        case 0: s = new JsonSink(bos); break;
        case 1: s = new JsonSink(bos, true); break;
        case 2: s = new JsonSink(bos, false, true); break;
        case 3: s = new JsonSink(bos, true); s.setPrePostRamble("PRE<", ">POST"); break;
        case 4: s = new JsonSink(bos, false); s.setIndent(true); s.setIndent(false); break;
        default: s = new JSONSinkAsArray(bos); break;
      }
      s.start(); boolean x1 = s.add(r1); boolean x2 = s.add(r2); boolean x3 = s.add(null); s.stop();
      p("jsonSink."+v+".beforeClose", bos.toString("UTF-8"));
      s.close();
      p("jsonSink."+v, x1+","+x2+","+x3+"|"+bos.toString("UTF-8"));
    }
    // Smile mode writes through a Writer
    { ByteArrayOutputStream bos = new ByteArrayOutputStream();
      JsonSink s = new JsonSink(bos, true, true, false);
      p("jsonSink.smile", tryIt(() -> { s.start(); return s.add(r1) + "|" + hex(bos.toByteArray()); }));
      p("jsonSink.smile.after", tryIt(() -> { s.stop(); s.close(); return hex(bos.toByteArray()); })); }
    // JsonSink on a closed stream: add() reports false
    { OutputStream bad = new OutputStream(){ public void write(int b) throws IOException { throw new IOException("boom"); } };
      JsonSink s = new JsonSink(bad); p("jsonSink.ioError", tryIt(() -> s.add(r1) + "," + s.add(r2))); }
    // BaseFile JSON read/write
    for (String ext : new String[]{"json", "json.gz"}) {
      BaseFile bf = fs.getFile("w." + ext);
      bf.writeJson(r2);
      p("baseFile.writeJson." + ext, tryIt(() -> { try (Reader r = bf.getReader()) { StringBuilder sb=new StringBuilder(); int c; while((c=r.read())!=-1) sb.append((char)c); return sb; } }));
      p("baseFile.readJsonElement." + ext, tryIt(() -> { JsonNode n = bf.readJsonElement(); return n + " " + types(n); }));
      p("baseFile.getJsonNode." + ext, tryIt(() -> bf.getJsonNode()));
    }
    String[][] inputs = {
      {"empty", ""}, {"ws", "  \n "}, {"bad", "{\"a\": ]"}, {"trailing", "{\"a\":1} {\"b\":2}"}, {"trailingGarbage", "{\"a\":1} xyz"},
      {"scalar", "42"}, {"string", "\"s\""}, {"array", "[1,2.5,\"x\",null,true]"}, {"nullLit", "null"}, {"dupKeys", "{\"a\":1,\"a\":2}"},
      {"comment", "{/*c*/\"a\":1}"}, {"single", "{'a':1}"}, {"nan", "{\"a\":NaN}"}, {"ctrl", "{\"a\":\"x\ty\"}"}, {"bigExp", "{\"a\":1e400,\"b\":1.5e3}"}};
    for (String[] in : inputs) {
      Path f = dir.resolve("in-" + in[0] + ".json"); Files.write(f, in[1].getBytes("UTF-8"));
      BaseFile bf = fs.getFile("in-" + in[0] + ".json");
      p("read." + in[0] + ".readJsonElement", tryIt(() -> { JsonNode n = bf.readJsonElement(); return n == null ? "null" : n.getClass().getSimpleName() + " " + n + " " + types(n); }));
      p("read." + in[0] + ".getJsonNode", tryIt(() -> { JsonNode n = bf.getJsonNode(); return n == null ? "null" : n.getClass().getSimpleName() + " " + n; }));
      p("read." + in[0] + ".iterator", tryIt(() -> { StringBuilder sb = new StringBuilder(); BaseJsonIterator it = new BaseJsonIterator(bf); while (it.hasNext()) { JsonNode n = it.next(); sb.append(n.getClass().getSimpleName()).append(' ').append(n).append(' ').append(types(n)).append(" | "); } it.close(); return sb; }));
      p("read." + in[0] + ".iteratorReader", tryIt(() -> { StringBuilder sb = new StringBuilder(); BaseJsonIterator it = new BaseJsonIterator(new StringReader(in[1])); while (it.hasNext()) sb.append(it.next()).append(" | "); return sb; }));
      p("read." + in[0] + ".iteratorStream", tryIt(() -> { StringBuilder sb = new StringBuilder(); BaseJsonIterator it = new BaseJsonIterator(new ByteArrayInputStream(in[1].getBytes("UTF-8"))); while (it.hasNext()) sb.append(it.next()).append(" | "); return sb; }));
    }
    // multi-document stream through the iterator
    { String multi = doc1 + "\n" + doc2 + "\n[1,{\"k\":\"v\"}] 7 \"s\" {\"x\":{\"y\":[{}]}}";
      p("iterator.multi", tryIt(() -> { StringBuilder sb = new StringBuilder(); BaseJsonIterator it = new BaseJsonIterator(new StringReader(multi)); while (it.hasNext()) { JsonNode n = it.next(); sb.append(n).append(' ').append(types(n)).append(" | "); } return sb; })); }
    // Name2JsonMapper
    Files.write(dir.resolve("dom_thing.json"), doc1.getBytes("UTF-8"));
    Files.write(dir.resolve("dom_broken.json"), "{".getBytes("UTF-8"));
    Name2JsonMapper nm = new Name2JsonMapper(fs.getFile(""), "dom");
    for (String k : new String[]{"thing", "DOM_Thing", "broken", "missing", ""}) p("name2json." + k, tryIt(() -> nm.apply(k)));
    // Round trip: a sink's output read back by the iterator
    { ByteArrayOutputStream bos = new ByteArrayOutputStream(); JsonSink s = new JsonSink(bos, true); s.start(); s.add(r1); s.add(r2); s.stop(); s.close();
      p("roundTrip", tryIt(() -> { StringBuilder sb = new StringBuilder(); BaseJsonIterator it = new BaseJsonIterator(new ByteArrayInputStream(bos.toByteArray())); while (it.hasNext()) { JsonNode n = it.next(); sb.append(n).append(' ').append(types(n)).append(" | "); } return sb; })); }
      return out;
  }

  @Test
  void matchesJackson2Output(@TempDir Path dir) throws Exception {
    Map<String, String> expected = new LinkedHashMap<>();
    try (InputStream in = getClass().getResourceAsStream("jackson2-basefile.txt")) {
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        int i = line.indexOf('=');
        expected.put(line.substring(0, i), line.substring(i + 1));
      }
    }
    Map<String, String> actual = new LinkedHashMap<>();
    compute(dir).forEach((k, v) -> actual.put(k, v.replace("StringNode", "TextNode").replace("StreamReadException", "JsonParseException")));
    assertThat(expected).hasSize(103);
    assertThat(actual).containsExactlyEntriesOf(expected);
  }
  /** 3.x threw the stream's IOException from writeJson; Jackson 3 would wrap it in an unchecked JacksonIOException. */
  @Test
  void writeJsonFailureIsStillAnIOException(@TempDir Path dir) throws Exception {
    BaseFile bf = org.mockito.Mockito.spy(new com.hitorro.util.basefile.fs.file.FileFileSystem(dir.toFile()).getFile("x.json"));
    OutputStream failing = new OutputStream() {
      public void write(int b) throws IOException { throw new IOException("disk full"); }
      public void write(byte[] b, int o, int l) throws IOException { throw new IOException("disk full"); }
    };
    org.mockito.Mockito.doReturn(failing).when(bf).getOutputStream();
    JsonNode n = com.hitorro.util.json.JsonMappers.json().readTree("{\"a\":1}");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> bf.writeJson(n))
        .isExactlyInstanceOf(IOException.class).hasMessage("disk full");
  }
}
