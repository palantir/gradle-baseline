/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.baseline.errorprone;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public final class Slf4jLogsafeArgsTest {

    private static final ImmutableList<String> LOG_LEVELS = ImmutableList.of("trace", "debug", "info", "warn", "error");

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(Slf4jLogsafeArgs.class, getClass());
    }

    private void test(String logArgs, String failingArgs) throws Exception {
        LOG_LEVELS.forEach(logLevel -> compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import java.util.ArrayList;",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "import org.slf4j.Marker;",
                        "import java.util.Iterator;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(String param) {",
                        "    // BUG: Diagnostic contains: does not use logsafe parameters for arguments " + failingArgs,
                        "    log." + logLevel + "(" + logArgs + ");",
                        "  }",
                        "",
                        "  class DummyMarker implements Marker {",
                        "    public String getName() { return null; }",
                        "    public void add(Marker reference) {}",
                        "    public boolean remove(Marker reference) { return true; }",
                        "    public boolean hasChildren() { return false; }",
                        "    public boolean hasReferences() { return false; }",
                        "    public Iterator<Marker> iterator() { return null; }",
                        "    public boolean contains(Marker other) { return false; }",
                        "    public boolean contains(String name) { return false; }",
                        "  }",
                        "}")
                .doTest());
    }

    @Test
    public void testFailingLogsafeArgs() throws Exception {
        // log.<>(String, Object)"
        test("\"constant {}\", \"string\"", "[1]");

        // log.<>(String, Object, Object)"
        test("\"constant {}\", \"string\", new ArrayList(1){}", "[1, 2]");

        // log.<>(String, Object, Arg<T>)"
        test("\"constant {} {}\", \"string\", SafeArg.of(\"name\", \"string\")", "[1]");

        // log.<>(Marker, String, Object, Arg<T>)"
        test("new DummyMarker(), \"constant {} {}\", \"string\", SafeArg.of(\"name\", \"string\")", "[2]");

        // log.<>(Marker, String, Object, Arg<T>, Exception)"
        test("new DummyMarker(), \"constant {} {}\", \"string\", UnsafeArg.of(\"name\", \"string\"), new Exception()",
                "[2]");

        // log.<>(String, Object, Arg<T>, Exception)"
        test("\"constant {} {}\", \"string\", SafeArg.of(\"name\", \"string\"), new Exception()", "[1]");
    }

    @Test
    public void testPassingLogsafeArgs() throws Exception {
        LOG_LEVELS.forEach(logLevel -> compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "import org.slf4j.Marker;",
                        "import java.util.Iterator;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  private static final String compileTimeConstant = \"constant\";",
                        "  void f() {",
                        "",
                        "    // log.<>(String)",
                        "    log." + logLevel + "(\"constant\");",
                        "    log." + logLevel + "(\"constant\" + compileTimeConstant);",
                        "",
                        "    // log.<>(String, Arg<T>)",
                        "    log." + logLevel + "(\"constant {}\", SafeArg.of(\"name\", \"string\"));",
                        "",
                        "    // log.<>(String, Arg<T>, Arg<T>)",
                        "    log." + logLevel + "(\"constant {} {}\",",
                        "        SafeArg.of(\"name\", \"string\"),",
                        "        UnsafeArg.of(\"name2\", \"string2\"));",
                        "",
                        "    // log.<>(Marker, String)",
                        "    log." + logLevel + "(new DummyMarker(), \"constant\");",
                        "",
                        "    // log.<>(Marker, String, Arg<T>)",
                        "    log." + logLevel + "(new DummyMarker(), \"constant {}\",",
                        "        SafeArg.of(\"name\", \"string\"));",
                        "",
                        "    // log.<>(Marker, String, Arg<T>, Arg<T>)",
                        "    log." + logLevel + "(new DummyMarker(), \"constant {} {}\",",
                        "        SafeArg.of(\"name\", \"string\"),",
                        "        UnsafeArg.of(\"name2\", \"string2\"));",
                        "",
                        "    // log.<>(String, Exception)",
                        "    log." + logLevel + "(\"constant\", new Exception());",
                        "    log." + logLevel + "(\"constant\" + compileTimeConstant, new Exception());",
                        "",
                        "    // log.<>(String, Arg<T>, Exception)",
                        "    log." + logLevel + "(\"constant {}\", SafeArg.of(\"name\", \"string\"), new Exception());",
                        "",
                        "    // log.<>(String, Arg<T>, Arg<T>, Exception)",
                        "    log." + logLevel + "(\"constant {} {}\",",
                        "        SafeArg.of(\"name\", \"string\"),",
                        "        UnsafeArg.of(\"name2\", \"string2\"),",
                        "        new Exception());",
                        "",
                        "    // log.<>(Marker, String, Exception)",
                        "    log." + logLevel + "(new DummyMarker(), \"constant\", new Exception());",
                        "",
                        "    // log.<>(Marker, String, Arg<T>, Exception)",
                        "    log." + logLevel + "(new DummyMarker(), \"constant {}\",",
                        "        SafeArg.of(\"name\", \"string\"),",
                        "        new Exception());",
                        "",
                        "    // log.<>(Marker, String, Arg<T>, Arg<T>, Exception)",
                        "    log." + logLevel + "(new DummyMarker(), \"constant {} {}\",",
                        "        SafeArg.of(\"name\", \"string\"),",
                        "        UnsafeArg.of(\"name2\", \"string2\"),",
                        "        new Exception());",
                        "",
                        "  }",
                        "",
                        "  class DummyMarker implements Marker {",
                        "    public String getName() { return null; }",
                        "    public void add(Marker reference) {}",
                        "    public boolean remove(Marker reference) { return true; }",
                        "    public boolean hasChildren() { return false; }",
                        "    public boolean hasReferences() { return false; }",
                        "    public Iterator<Marker> iterator() { return null; }",
                        "    public boolean contains(Marker other) { return false; }",
                        "    public boolean contains(String name) { return false; }",
                        "  }",
                        "}")
                .doTest());
    }
}
