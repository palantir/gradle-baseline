/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class PreferSafeLoggableExceptionsTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(PreferSafeLoggableExceptions.class, getClass());
    }

    @Test
    public void illegal_argument_exception() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "class Bean {",
                        "// BUG: Diagnostic contains: Prefer SafeIllegalArgumentException",
                        "Exception foo = new IllegalArgumentException(\"Foo\");",
                        "}")
                .doTest();
    }

    @Test
    public void auto_fix_illegal_argument_exception() {
        RefactoringValidator.of(new PreferSafeLoggableExceptions(), getClass())
                .addInputLines(
                        "Bean.java",
                        "class Bean {",
                        "  Exception foo = new IllegalArgumentException(\"Foo\");",
                        "}",
                        "")
                .addOutputLines(
                        "Bean.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;",
                        "",
                        "class Bean {",
                        "  Exception foo = new SafeIllegalArgumentException(\"Foo\");",
                        "}",
                        "")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void illegal_argument_exception_without_message_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "class Bean {",
                        "Exception foo = new IllegalArgumentException(new RuntimeException());",
                        "}")
                .doTest();
    }

    @Test
    public void auto_fix_illegal_state_exception() {
        RefactoringValidator.of(new PreferSafeLoggableExceptions(), getClass())
                .addInputLines(
                        "Bean.java",
                        "class Bean {",
                        "  Exception foo = new IllegalArgumentException(\"Foo\");",
                        "}",
                        "")
                .addOutputLines(
                        "Bean.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;",
                        "",
                        "class Bean {",
                        "  Exception foo = new SafeIllegalArgumentException(\"Foo\");",
                        "}",
                        "")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void illegal_state_exception() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "class Bean {",
                        "// BUG: Diagnostic contains: Prefer SafeIllegalStateException",
                        "Exception foo = new IllegalStateException(\"Foo\");",
                        "}")
                .doTest();
    }

    @Test
    public void illegal_state_exception_without_message_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "class Bean {",
                        "Exception foo = new IllegalStateException(new RuntimeException());",
                        "}")
                .doTest();
    }

    @Test
    public void illegal_state_exception_with_non_constant_message_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "class Bean {",
                        "Exception foo = new IllegalStateException(\"I am a non-constant string\" + Math.random());",
                        "}")
                .doTest();
    }

    @Test
    public void illegal_state_exception_in_junit4_test_method_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "FooTest.java",
                        "import org.junit.Test;",
                        "class FooTest {",
                        "  @Test",
                        "  public void run_junit4_test() {",
                        "    throw new IllegalStateException(\"constant\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void illegal_state_exception_in_junit5_test_method_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "FooTest.java",
                        "import org.junit.jupiter.api.Test;",
                        "import org.junit.jupiter.api.TestTemplate;",
                        "class FooTest {",
                        "  @Test",
                        "  public void run_junit5_test() {",
                        "    throw new IllegalStateException(\"constant\");",
                        "  }",
                        "  @TestTemplate",
                        "  public void junit5_test_template() {",
                        "    throw new IllegalStateException(\"constant\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void illegal_state_exception_in_test_field_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "FooTest.java",
                        "import org.junit.Test;",
                        "class FooTest {",
                        "  Exception foo = new IllegalStateException(\"constant\");",
                        "  @Test",
                        "  public void doSomething() {}",
                        "}")
                .doTest();
    }

    @Test
    public void illegal_state_exception_with_assertj_import_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "class Foo {",
                        "  public void f() {",
                        "    throw new IllegalStateException(\"constant\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void io_exception() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "class Bean {",
                        "// BUG: Diagnostic contains: Prefer SafeIoException",
                        "Exception foo = new java.io.IOException(\"Foo\");",
                        "}")
                .doTest();
    }

    @Test
    public void io_exception_without_message_doesnt_match() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "class Bean {",
                        "Exception foo = new java.io.IOException(new RuntimeException());",
                        "}")
                .doTest();
    }
}
