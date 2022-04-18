/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class LoggingDoNotLogTest {

    @Test
    void testJul() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.util.logging.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@DoNotLog String in) {",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    Logger.getLogger(\"foo\").info(in);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testSystemLogger() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.util.logging.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@DoNotLog String in) {",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    System.getLogger(\"foo\").log(System.Logger.Level.INFO, in);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testLog4j1() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.apache.log4j.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@DoNotLog String in) {",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    LogManager.getLogger(\"foo\").info(in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    MDC.put(\"key\", in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    MDC.put(in, \"value\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testSlf4j() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@DoNotLog String in) {",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    LoggerFactory.getLogger(\"foo\").info(in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    MDC.put(\"key\", in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    MDC.put(in, \"value\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testException() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@DoNotLog String in) {",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    throw new RuntimeException(in);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testPreconditions() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.google.common.base.Preconditions;",
                        "class Test {",
                        "  void f(@DoNotLog String in) {",
                        "    Preconditions.checkNotNull(in, \"do-not-log allowed as the check arg\");",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    Preconditions.checkArgument(false, in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    Preconditions.checkNotNull(null, in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    Preconditions.checkState(false, in);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testValidate() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.apache.commons.lang3.Validate;",
                        "class Test {",
                        "  void f(@DoNotLog String in) {",
                        "    Validate.notNull(in, \"do-not-log allowed as the check arg\");",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    Validate.isTrue(false, in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    Validate.notNull(null, in);",
                        "    // BUG: Diagnostic contains: @DoNotLog types must not be passed to any logger",
                        "    Validate.validState(false, in);",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(LoggingDoNotLog.class, getClass());
    }
}
