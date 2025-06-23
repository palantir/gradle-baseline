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

class GradleExceptionUsageTest {

    @Test
    void testGradleError() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.gradle.api.GradleException;",
                        "class Test {",
                        "   void f() {",
                        "       // BUG: Diagnostic contains: Prefer throwing RuntimeException or another appropriate"
                                + " exception instead",
                        "       throw new GradleException();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testRethrowIsAllowed() {
        helper().addSourceLines(
                        "Test.java",
                        "import org.gradle.api.GradleException;",
                        "class Test {",
                        "   void f(GradleException e) {",
                        "       throw e;",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import org.gradle.api.GradleException;",
                        "class Test {",
                        "   void f1() {",
                        "       throw new GradleException();",
                        "   }",
                        "   void f2(Throwable t) {",
                        "       throw new GradleException(\"constant\", t);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import org.gradle.api.GradleException;",
                        "class Test {",
                        "   void f1() {",
                        "       throw new RuntimeException();",
                        "   }",
                        "   void f2(Throwable t) {",
                        "       throw new RuntimeException(\"constant\", t);",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(GradleExceptionUsage.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(GradleExceptionUsage.class, getClass());
    }
}
