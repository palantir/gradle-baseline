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
                        "import com.palantir.ri.ResourceIdentifier;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(String param) {",
                        "    // BUG: Diagnostic contains: Log statments do not use the GothamRidArg.of() helper for arguments " + failingArgs,
                        "    log." + logLevel + "(" + logArgs + ");",
                        "  }",
                        "}")
                .doTest());
    }

    @Test
    public void testFailingRidLogging() throws Exception {
        test("\"logMessage\", SafeArg.of(\"rid\", ResourceIdentifier.of(\"fakeRid\"))", "[1]");
        test("\"logMessage\", \"constant\", SafeArg.of(\"rid\", ResourceIdentifier.of(\"fakeRid\"))", "[2]");
        test("\"logMessage\", SafeArg.of(\"rid\", ResourceIdentifier.of(\"fakeRid\")), \"constant\"", "[1]");
        test("\"logMessage\", UnsafeArg.of(\"rid\", ResourceIdentifier.of(\"fakeRid\"))", "[1]");
        test("\"logMessage\", \"constant\", UnsafeArg.of(\"rid\", ResourceIdentifier.of(\"fakeRid\"))", "[2]");
        test("\"logMessage\", UnsafeArg.of(\"rid\", ResourceIdentifier.of(\"fakeRid\")), \"constant\"", "[1]");
    }

    @Test
    public void testValidRidLogging() throws Exception {
        // can only test this once the source file can import GothamRidArg (i.e. once its committed)
        // test("\"logMessage\", GothamRidArg.of(ResourceIdentifier.of(\"fakeRid\"))", "[]");
    }
}