/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

class LoggerInterpolationConsumesThrowableTest {

    @Test
    void testOneExtra_slf4j() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  static {",
                        "    // BUG: Diagnostic contains: Please remove 1 '{}' placeholder.",
                        "    LoggerFactory.getLogger(Test.class).error(\"{}\", new RuntimeException());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testOneExtra_safelogger() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.logger.*;",
                        "class Test {",
                        "  static {",
                        "    // BUG: Diagnostic contains: Please remove 1 '{}' placeholder.",
                        "    SafeLoggerFactory.get(Test.class).error(\"{}\", new RuntimeException());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testOneExtraWithMarker() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  static {",
                        "    // BUG: Diagnostic contains: Please remove 1 '{}' placeholder.",
                        "    LoggerFactory.getLogger(Test.class).error(",
                        "      MarkerFactory.getMarker(\"x\"), \"{}\", new RuntimeException());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testOneExtraWithParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  static {",
                        "    // BUG: Diagnostic contains: Please remove 1 '{}' placeholder.",
                        "    LoggerFactory.getLogger(Test.class).error(\"{} {}\", 1, new RuntimeException());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testTwoExtra() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  static {",
                        "    // BUG: Diagnostic contains: Please remove 2 '{}' placeholders.",
                        "    LoggerFactory.getLogger(Test.class).error(\"{} {}\", new RuntimeException());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testTwoExtraWithParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  static {",
                        "    // BUG: Diagnostic contains: Please remove 2 '{}' placeholders.",
                        "    LoggerFactory.getLogger(Test.class).error(\"{} {} {}\", 1, new RuntimeException());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testExtraParamsIgnoredWhenNoThrowableIsPresent() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "class Test {",
                        "  static {",
                        "    LoggerFactory.getLogger(Test.class).error(\"{} {} {}\", 1);",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(LoggerInterpolationConsumesThrowable.class, getClass());
    }
}
