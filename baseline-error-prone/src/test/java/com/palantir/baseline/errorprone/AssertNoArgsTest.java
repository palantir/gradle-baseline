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

import org.junit.jupiter.api.Test;

final class AssertNoArgsTest {

    @Test
    void testServiceExceptionAssert_hasArgs() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.conjure.java.api.testing.ServiceExceptionAssert;",
                        "public class Test {",
                        "  void f(ServiceExceptionAssert a) {",
                        "    a.hasArgs();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.conjure.java.api.testing.ServiceExceptionAssert;",
                        "public class Test {",
                        "  void f(ServiceExceptionAssert a) {",
                        "    a.hasNoArgs();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testServiceExceptionAssert_hasArgs_notEmpty() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.conjure.java.api.testing.ServiceExceptionAssert;",
                        "import com.palantir.logsafe.Arg;",
                        "public class Test {",
                        "  void f(ServiceExceptionAssert a, Arg<?> arg) {",
                        "    a.hasArgs(arg);",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testLoggableExceptionAssert_hasArgs() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a) {",
                        "    a.hasArgs();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a) {",
                        "    a.hasNoArgs();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testLoggableExceptionAssert_hasArgs_notEmpty() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.Arg;",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a, Arg<?> arg) {",
                        "    a.hasArgs(arg);",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testLoggableExceptionAssert_hasExactlyArgs() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a) {",
                        "    a.hasExactlyArgs();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a) {",
                        "    a.hasNoArgs();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testLoggableExceptionAssert_hasExactlyArgs_notEmpty() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.Arg;",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a, Arg<?> arg) {",
                        "    a.hasExactlyArgs(arg);",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testLoggableExceptionAssert_containsArgs() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a) {",
                        "    a.containsArgs();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a) {",
                        "    a.hasNoArgs();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testLoggableExceptionAssert_containsArgs_notEmpty() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.Arg;",
                        "import com.palantir.logsafe.testing.LoggableExceptionAssert;",
                        "public class Test {",
                        "  void f(LoggableExceptionAssert a, Arg<?> arg) {",
                        "    a.containsArgs(arg);",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(AssertNoArgs.class, getClass());
    }
}
