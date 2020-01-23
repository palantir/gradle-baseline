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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public final class Slf4jConstantLogMessageTests {

    private void test(String log) {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "import org.slf4j.Marker;",
                        "import java.util.Iterator;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(String param) {",
                        "    // BUG: Diagnostic contains: non-constant expression",
                        "    " + log,
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
                .doTest();
    }

    @Test
    public void testNonCompileTimeConstantExpression() throws Exception {
        test("log.trace(\"constant\" + param);");
        test("log.debug(\"constant\" + param);");
        test("log.info(\"constant\" + param);");
        test("log.warn(\"constant\" + param);");
        test("log.error(\"constant\" + param);");
        test("log.trace(new DummyMarker(), \"constant\" + param);");
        test("log.debug(new DummyMarker(), \"constant\" + param);");
        test("log.info(new DummyMarker(), \"constant\" + param);");
        test("log.warn(new DummyMarker(), \"constant\" + param);");
        test("log.error(new DummyMarker(), \"constant\" + param);");
    }

    @Test
    public void negative() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "import org.slf4j.Marker;",
                        "import java.util.Iterator;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  private static final String compileTimeConstant = \"constant\";",
                        "  void f() {",
                        "    // log.<>(String)",
                        "    log.trace(\"constant\");",
                        "    log.debug(\"constant\");",
                        "    log.info(\"constant\");",
                        "    log.warn(\"constant\");",
                        "    log.error(\"constant\");",
                        "",
                        "    // log.<>(String, Object)",
                        "    log.trace(\"constant {}\", \"extra\");",
                        "    log.debug(\"constant {}\", \"extra\");",
                        "    log.info(\"constant {}\", \"extra\");",
                        "    log.warn(\"constant {}\", \"extra\");",
                        "    log.error(\"constant {}\", \"extra\");",
                        "",
                        "    // log.<>(String, Object, Object)",
                        "    log.trace(\"constant {} {}\", \"extra\", \"extra2\");",
                        "    log.debug(\"constant {} {}\", \"extra\", \"extra2\");",
                        "    log.info(\"constant {} {}\", \"extra\", \"extra2\");",
                        "    log.warn(\"constant {} {}\", \"extra\", \"extra2\");",
                        "    log.error(\"constant {} {}\", \"extra\", \"extra2\");",
                        "",
                        "    // log.<>(String, Object...)",
                        "    log.trace(\"constant {} {} {}\", \"extra\", \"extra2\", \"extra3\");",
                        "    log.debug(\"constant {} {} {}\", \"extra\", \"extra2\", \"extra3\");",
                        "    log.info(\"constant {} {} {}\", \"extra\", \"extra2\", \"extra3\");",
                        "    log.warn(\"constant {} {} {}\", \"extra\", \"extra2\", \"extra3\");",
                        "    log.error(\"constant {} {} {}\", \"extra\", \"extra2\", \"extra3\");",
                        "",
                        "    log.trace(\"constant\" + compileTimeConstant);",
                        "    log.debug(\"constant\" + compileTimeConstant);",
                        "    log.info(\"constant\" + compileTimeConstant);",
                        "    log.warn(\"constant\" + compileTimeConstant);",
                        "    log.error(\"constant\" + compileTimeConstant);",
                        "",
                        "    log.trace(\"constant {}\" + compileTimeConstant, \"extra\");",
                        "    log.debug(\"constant {}\" + compileTimeConstant, \"extra\");",
                        "    log.info(\"constant {}\" + compileTimeConstant, \"extra\");",
                        "    log.warn(\"constant {}\" + compileTimeConstant, \"extra\");",
                        "    log.error(\"constant {}\" + compileTimeConstant, \"extra\");",
                        "",
                        "    // log.<>(Marker, String)",
                        "    log.trace(new DummyMarker(), \"constant\");",
                        "    log.debug(new DummyMarker(), \"constant\");",
                        "    log.info(new DummyMarker(), \"constant\");",
                        "    log.warn(new DummyMarker(), \"constant\");",
                        "    log.error(new DummyMarker(), \"constant\");",
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
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(Slf4jConstantLogMessage.class, getClass());
    }
}
