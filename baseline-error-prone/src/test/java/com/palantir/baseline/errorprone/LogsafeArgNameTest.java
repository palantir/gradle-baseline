/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

public final class LogsafeArgNameTest {

    @Test
    public void catches_unsafe_arg_name() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: must be marked as unsafe",
                        "    SafeArg.of(\"foo\", 1);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void catches_case_insensitive_unsafe_arg_name() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: must be marked as unsafe",
                        "    SafeArg.of(\"Foo\", 1);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_unsafe_arg_name() {
        getRefactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "  void f() {",
                        "    SafeArg.of(\"Foo\", 1);",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "class Test {",
                        "  void f() {",
                        "    UnsafeArg.of(\"Foo\", 1);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_safe_arg_names() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "class Test {",
                        "  void f() {",
                        "    SafeArg.of(\"baz\", 1);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void ignores_identifier_arg_names() {
        getCompilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import java.lang.String;",
                        "class Test {",
                        "  static final String NAME = \"name\";",
                        "  void f() {",
                        "    SafeArg.of(NAME, 1);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    private static RefactoringValidator getRefactoringHelper() {
        return RefactoringValidator.of(
                LogsafeArgName.class,
                LogsafeArgNameTest.class,
                "-XepOpt:" + LogsafeArgName.UNSAFE_ARG_NAMES_FLAG + "=foo,bar");
    }

    private static CompilationTestHelper getCompilationHelper() {
        return CompilationTestHelper.newInstance(LogsafeArgName.class, LogsafeArgNameTest.class)
                .setArgs("-XepOpt:" + LogsafeArgName.UNSAFE_ARG_NAMES_FLAG + "=foo,bar");
    }
}
