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
import com.google.errorprone.ErrorProneFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class LogsafeArgNameTest {
    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(LogsafeArgName.class, getClass())
                .setArgs(ImmutableList.of("-XepOpt:" + LogsafeArgName.UNSAFE_ARG_NAMES_FLAG + "=foo,bar"));
        refactoringHelper = RefactoringValidator.of(
                new LogsafeArgName(ErrorProneFlags.builder()
                        .putFlag(LogsafeArgName.UNSAFE_ARG_NAMES_FLAG, "foo,bar")
                        .build()),
                getClass());
    }

    @Test
    public void catches_unsafe_arg_name() {
        compilationHelper
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
        compilationHelper
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
        refactoringHelper
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
        compilationHelper
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
}
