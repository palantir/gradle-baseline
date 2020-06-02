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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

public final class ExtendsErrorOrThrowableTest {
    @Test
    void testSimple() {
        helper().addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: Class should not extend java.lang.Error",
                        "public class Test extends Error {",
                        "  public Test() {}",
                        "}")
                .doTest();
    }

    @Test
    void testNonJavaLangError() {
        helper().addSourceLines("Error.java", "public class Error {", "  public Error() {}", "}")
                .addSourceLines("Test.java", "public class Test extends Error {", "  public Test() {}", "}")
                .doTest();
    }

    @Test
    void testSimpleException() {
        helper().addSourceLines("Test.java", "public class Test extends RuntimeException {", "  public Test() {}", "}")
                .expectNoDiagnostics()
                .doTest();
    }

    @Test
    void testSpecificJavaError() {
        helper().addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: Class should not extend java.lang.Error",
                        "public class Test extends OutOfMemoryError {",
                        "  public Test() {}",
                        "}")
                .doTest();
    }

    @Test
    void testSpecificJavaErrorNoFix() {
        fix().addInputLines("Test.java", "public class Test extends OutOfMemoryError {", "  public Test() {}", "}")
                .expectUnchanged()
                .doTestExpectingFailure(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFixError() {
        fix().addInputLines("Test.java", "public class Test extends Error {", "  public Test() {}", "}")
                .addOutputLines("Test.java", "public class Test extends RuntimeException {", "  public Test() {}", "}")
                .doTest();
    }

    @Test
    void testThrowableDiagnostic() {
        helper().addSourceLines(
                        "Test.java",
                        "// BUG: Diagnostic contains: Class should not extend java.lang.Error",
                        "public class Test extends Throwable {",
                        "  public Test() {}",
                        "}")
                .doTest();
    }

    @Test
    void testFixThrowable() {
        fix().addInputLines("Test.java", "public class Test extends Throwable {", "  public Test() {}", "}")
                .addOutputLines("Test.java", "public class Test extends RuntimeException {", "  public Test() {}", "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ExtendsErrorOrThrowable.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new ExtendsErrorOrThrowable(), getClass());
    }
}
