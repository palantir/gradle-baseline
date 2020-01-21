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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class ThrowErrorTest {

    @Test
    void testAssertionError() {
        helper().addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   void f() {",
                        "       // BUG: Diagnostic contains: Prefer throwing a RuntimeException",
                        "       throw new AssertionError();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testError() {
        helper().addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   void f() {",
                        "       // BUG: Diagnostic contains: Prefer throwing a RuntimeException",
                        "       throw new Error();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testError_testCode() {
        // It's common to avoid handling Error by catching and rethrowing, this should be allowed. This check
        // is meant to dissuade developers from creating and throwing new Errors.
        helper().addSourceLines(
                        "TestCase.java",
                        "import " + Test.class.getName() + ';',
                        "class TestCase {",
                        "   @Test",
                        "   void f() {",
                        "       throw new Error();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testRethrowIsAllowed() {
        helper().addSourceLines("Test.java", "class Test {", "   void f(Error e) {", "       throw e;", "   }", "}")
                .doTest();
    }

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "   void f1() {",
                        "       throw new AssertionError();",
                        "   }",
                        "   void f2(String nonConstant) {",
                        "       throw new AssertionError(nonConstant);",
                        "   }",
                        "   void f3() {",
                        "       throw new AssertionError(\"constant\");",
                        "   }",
                        "   void f4(String nonConstant, Throwable t) {",
                        "       throw new AssertionError(nonConstant, t);",
                        "   }",
                        "   void f5(Throwable t) {",
                        "       throw new AssertionError(\"constant\", t);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.exceptions.SafeIllegalStateException;",
                        "class Test {",
                        "   void f1() {",
                        "       throw new IllegalStateException();",
                        "   }",
                        "   void f2(String nonConstant) {",
                        "       throw new IllegalStateException(nonConstant);",
                        "   }",
                        "   void f3() {",
                        "       throw new SafeIllegalStateException(\"constant\");",
                        "   }",
                        "   void f4(String nonConstant, Throwable t) {",
                        "       throw new IllegalStateException(nonConstant, t);",
                        "   }",
                        "   void f5(Throwable t) {",
                        "       throw new SafeIllegalStateException(\"constant\", t);",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ThrowError.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ThrowError(), getClass());
    }
}
