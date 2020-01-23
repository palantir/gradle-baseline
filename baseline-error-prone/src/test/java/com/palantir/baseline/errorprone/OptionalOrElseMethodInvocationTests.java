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

public final class OptionalOrElseMethodInvocationTests {

    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringTestHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(OptionalOrElseMethodInvocation.class, getClass());
        refactoringTestHelper = RefactoringValidator.of(new OptionalOrElseMethodInvocation(), getClass());
    }

    @Test
    public void testMethodInvocation() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  // BUG: Diagnostic contains: invokes a method",
                        "  private final String string = Optional.of(\"hello\").orElse(f());",
                        "}")
                .doTest();
    }

    @Test
    public void testContainsMethodInvocation() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  // BUG: Diagnostic contains: invokes a method",
                        "  private final String string = Optional.of(\"hello\").orElse(\"world\" + f());",
                        "}")
                .doTest();
    }

    @Test
    public void testConstructor() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: invokes a method",
                        "  private final String string = Optional.of(\"hello\").orElse(new String());",
                        "}")
                .doTest();
    }

    @Test
    public void testContainsConstructor() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: invokes a method",
                        "  private final String string = Optional.of(\"hello\").orElse(\"world\" + new String());",
                        "}")
                .doTest();
    }

    @Test
    public void testLiteral() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  private final String string = Optional.of(\"hello\").orElse(\"constant\");",
                        "}")
                .doTest();
    }

    @Test
    public void testConstant() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  private static final String constant = \"constant\";",
                        "  private final String string = Optional.of(\"hello\").orElse(constant);",
                        "}")
                .doTest();
    }

    @Test
    public void testVariable() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  void test() {",
                        "    String variable = f();",
                        "    Optional.of(\"hello\").orElse(variable);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testMethodReference() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "import java.util.function.Supplier;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  private final Optional<Supplier<String>> optionalSupplier = Optional.of(() -> \"hello\");",
                        "  private final Supplier<String> supplier = optionalSupplier.orElse(this::f);",
                        "}")
                .doTest();
    }

    @Test
    public void testOrElseGet() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  private final String string = Optional.of(\"hello\").orElseGet(() -> f());",
                        "}")
                .doTest();
    }

    @Test
    public void testReplacement() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  private final String string = Optional.of(\"hello\").orElse(f());",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  private final String string = Optional.of(\"hello\").orElseGet(() -> f());",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
