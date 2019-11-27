/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.ri.ResourceIdentifier;
import org.junit.jupiter.api.Test;

public class RidSafeLoggingTest {

    private static final ImmutableList<String> LOG_LEVELS = ImmutableList.of("trace", "debug", "info", "warn", "error");

    private void test(String logArgs, String failingArgs) throws Exception {
        LOG_LEVELS.forEach(logLevel -> CompilationTestHelper.newInstance(RidSafeLogging.class, getClass())
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
        test("SafeArg.of(\"rid\", \"ResourceIdentifier.of(\"fakeRid\")\")", "[1]");
    }
}
