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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class OptionalOrElseGetConstantTest {

    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringTestHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(OptionalOrElseGetConstant.class, getClass());
        refactoringTestHelper = RefactoringValidator.of(new OptionalOrElseGetConstant(), getClass());
    }

    @Test
    public void testOrElseGetStringLiteral() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "    String f() { return \"hello\"; }",
                        "    // BUG: Diagnostic contains: Prefer Optional#orElse",
                        "    private final String string = Optional.of(\"hello\").orElseGet(() -> \"world\");",
                        "}")
                .doTest();
    }

    @Test
    public void testOrElseGetConstantString() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "    private static final String constant = \"constant\";",
                        "    // BUG: Diagnostic contains: Prefer Optional#orElse",
                        "    private final String string = Optional.of(\"hello\").orElseGet(() -> constant);",
                        "}")
                .doTest();
    }

    @Test
    public void testReplacementOfLiteral() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "    private final boolean b = Optional.of(true).orElseGet(() -> false);",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "    private final boolean b = Optional.of(true).orElse(false);",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
