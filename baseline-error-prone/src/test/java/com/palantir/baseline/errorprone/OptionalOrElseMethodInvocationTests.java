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
import org.junit.Before;
import org.junit.Test;

public final class OptionalOrElseMethodInvocationTests {

    private CompilationTestHelper compilationHelper;
    private BugCheckerRefactoringTestHelper refactoringTestHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(OptionalOrElseMethodInvocation.class, getClass());
        refactoringTestHelper = BugCheckerRefactoringTestHelper.newInstance(
                new OptionalOrElseMethodInvocation(), getClass());
    }

    private void test(String expr) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  String s = \"world\";",
                        "  // BUG: Diagnostic contains: invokes a method",
                        "  private final String string = Optional.of(\"hello\").orElse(" + expr + ");",
                        "}")
                .doTest();
    }

    @Test
    public void testNonCompileTimeConstantExpression() {
        test("f()");
        test("s + s");
        test("\"world\" + s");
        test("\"world\".substring(1)");
    }

    @Test
    public void testNonCompileTimeConstantExpression_replacement() {
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

    @Test
    public void negative() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  private static final String compileTimeConstant = \"constant\";",
                        "  String f() { return \"hello\"; }",
                        "  void test() {",
                        "    Optional.of(\"hello\").orElse(\"constant\");",
                        "    Optional.of(\"hello\").orElse(compileTimeConstant);",
                        "    String string = f();",
                        "    Optional.of(\"hello\").orElse(string);",
                        "    Optional.of(\"hello\").orElseGet(() -> f());",
                        "  }",
                        "}")
                .doTest();
    }

}
