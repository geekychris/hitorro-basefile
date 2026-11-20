/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.util.core.iterator.sinks;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins how {@link JsonSink} treats its target stream, which the format golden test can't see
 * (it writes to a {@code ByteArrayOutputStream}, where close and flush do nothing). 3.x turned off
 * Jackson's {@code AUTO_CLOSE_TARGET} and {@code FLUSH_PASSED_TO_STREAM}; 4.0.0 does the same with
 * {@code StreamWriteFeature}. Without them the first row closes the target and every later row is
 * lost. The expected lines are what hitorro-basefile 3.0.2 (Jackson 2.18) produced.
 */
class JsonSinkStreamHandlingTest {
  /** Records flush/close calls on the sink's target stream and refuses writes after close. */
  public static final class Recording extends OutputStream {
    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    int flushes, closes;
    @Override public void write(int b) throws IOException { if (closes > 0) throw new IOException("write after close"); buf.write(b); }
    @Override public void write(byte[] b, int o, int l) throws IOException { if (closes > 0) throw new IOException("write after close"); buf.write(b, o, l); }
    @Override public void flush() { flushes++; }
    @Override public void close() { closes++; }
    String state() { return "flushes=" + flushes + ",closes=" + closes + ",bytes=" + buf.size(); }
  }
  static String generate() throws Exception {
    StringBuilder out = new StringBuilder();
    String[] modes = {"compact", "indent", "flushOnAdd"};
    for (String mode : modes) {
      Recording r = new Recording();
      JsonSink s = switch (mode) {
        case "compact" -> new JsonSink(r, false);
        case "indent" -> new JsonSink(r, true);
        default -> new JsonSink(r, false, true);
      };
      List<String> steps = new ArrayList<>();
      try {
        s.start(); steps.add("start:" + r.state());
        for (int i = 0; i < 3; i++) { s.add(com.hitorro.util.json.JsonMappers.json().readTree("{\"i\":" + i + ",\"s\":\"x\"}")); steps.add("add" + i + ":" + r.state()); }
        s.stop(); steps.add("stop:" + r.state());
        s.close(); steps.add("close:" + r.state());
      } catch (Throwable t) { steps.add("!" + t.getClass().getSimpleName() + ":" + r.state()); }
      out.append("jsonsink.").append(mode).append('=').append(String.join(" ", steps)).append('\n');
    }
    return out.toString();
  }

  @Test
  void flushesAndClosesTheTargetAsOnJackson2() throws Exception {
    List<String> expected = List.of(
        "jsonsink.compact=start:flushes=0,closes=0,bytes=0 add0:flushes=0,closes=0,bytes=0 add1:flushes=0,closes=0,bytes=0 add2:flushes=0,closes=0,bytes=0 stop:flushes=1,closes=0,bytes=47 close:flushes=2,closes=1,bytes=47",
        "jsonsink.indent=start:flushes=0,closes=0,bytes=0 add0:flushes=0,closes=0,bytes=0 add1:flushes=0,closes=0,bytes=0 add2:flushes=0,closes=0,bytes=0 stop:flushes=1,closes=0,bytes=80 close:flushes=2,closes=1,bytes=80",
        "jsonsink.flushOnAdd=start:flushes=0,closes=0,bytes=0 add0:flushes=1,closes=0,bytes=15 add1:flushes=2,closes=0,bytes=31 add2:flushes=3,closes=0,bytes=47 stop:flushes=4,closes=0,bytes=47 close:flushes=5,closes=1,bytes=47");
    assertEquals(String.join("\n", expected), generate().strip());
  }
}
