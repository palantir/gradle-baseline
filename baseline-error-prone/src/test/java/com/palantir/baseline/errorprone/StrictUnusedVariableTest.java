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

import com.google.errorprone.BugCheckerRefactoringTestHelper.TestMode;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class StrictUnusedVariableTest {

    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringTestHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(StrictUnusedVariable.class, getClass());
        refactoringTestHelper = RefactoringValidator.of(new StrictUnusedVariable(), getClass());
    }

    @Test
    public void handles_interface() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "interface Test {",
                        "  void method(String param);",
                        "  // BUG: Diagnostic contains: Unused",
                        "  default void defaultMethod(String param) { }",
                        "}")
                .doTest();
    }

    @Test
    public void handles_abstract_classes() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "abstract class Test {",
                        "  abstract void method(String param);",
                        "  // BUG: Diagnostic contains: Unused",
                        "  void defaultMethod(String param) { }",
                        "  // BUG: Diagnostic contains: Unused",
                        "  private void privateMethod(String param) { }",
                        "}")
                .doTest();
    }

    @Test
    public void handles_classes() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  // BUG: Diagnostic contains: Unused",
                        "   Test(String foo) { }",
                        "  // BUG: Diagnostic contains: Unused",
                        "  private static void privateMethod(String buggy) { }",
                        "  // BUG: Diagnostic contains: Unused",
                        "  public static void publicMethod(String buggy) { }",
                        "}")
                .doTest();
    }

    @Test
    public void handles_enums() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "enum Test {",
                        "  INSTANCE;",
                        "  // BUG: Diagnostic contains: Unused",
                        "  private static void privateMethod(String buggy) { }",
                        "  // BUG: Diagnostic contains: Unused",
                        "  public static void publicMethod(String buggy) { }",
                        "}")
                .doTest();
    }

    @Test
    public void renames_previous_suppression() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  public void publicMethod(String unusedValue, String unusedValue2) { }",
                        "  public void varArgs(String unusedValue, String... unusedValue2) { }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  public void publicMethod(String _value, String _value2) { }",
                        "  public void varArgs(String _value, String... _value2) { }",
                        "}")
                .doTest(TestMode.TEXT_MATCH);
    }

    @Test
    public void renames_unused_param() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  private void privateMethod(String value) { }",
                        "  public void publicMethod(String value, String value2) { }",
                        "  public void varArgs(String value, String... value2) { }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  private void privateMethod() { }",
                        "  public void publicMethod(String _value, String _value2) { }",
                        "  public void varArgs(String _value, String... _value2) { }",
                        "}")
                .doTest(TestMode.TEXT_MATCH);
    }

    @Test
    public void fails_suppressed_but_used_variables() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: Unused",
                        "  private static final String _field = \"\";",
                        "  // BUG: Diagnostic contains: Unused",
                        "  public static void privateMethod(String _value) {",
                        "    System.out.println(_value);",
                        "  // BUG: Diagnostic contains: Unused",
                        "    String _bar = \"bar\";",
                        "    System.out.println(_bar);",
                        "    System.out.println(_field);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void side_effects_are_preserved() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  private static int _field = 1;",
                        "  public static void privateMethod() {",
                        "    Object foo = someMethod();",
                        "  }",
                        "  private static Object someMethod() { return null; }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  private static int _field = 1;",
                        "  public static void privateMethod() {",
                        "    someMethod();",
                        "  }",
                        "  private static Object someMethod() { return null; }",
                        "}")
                .doTest(TestMode.TEXT_MATCH);
    }

    @Test
    public void fixes_suppressed_but_used_variables() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  private static int _field = 1;",
                        "  public static void privateMethod(int _value, int _value2) {",
                        "    int _value3 = 1;",
                        "    _value3 = 2;",
                        "    System.out.println(_value);",
                        "    System.out.println(_value2 + _value3 + _field);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  private static int field = 1;",
                        "  public static void privateMethod(int value, int value2) {",
                        "    int value3 = 1;",
                        "    value3 = 2;",
                        "    System.out.println(value);",
                        "    System.out.println(value2 + value3 + field);",
                        "  }",
                        "}")
                .doTestExpectingFailure(TestMode.TEXT_MATCH);
    }

    @Test
    public void fixes_previously_suppressed_variables() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java", "class Test {", "  public static void privateMethod(int unused) {", "  }", "}")
                .addOutputLines(
                        "Test.java", "class Test {", "  public static void privateMethod(int _value) {", "  }", "}")
                .doTest(TestMode.TEXT_MATCH);
    }

    @Test
    public void testSuppression() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  public static void a(@SuppressWarnings(\"StrictUnusedVariable\") int val) {}",
                        "  public static void b(@SuppressWarnings(\"UnusedVariable\") int val) {}",
                        "  public static void c(@SuppressWarnings(\"unused\") int val) {}",
                        "  public static void d(int _val) {}",
                        "}")
                .expectUnchanged()
                .doTest(TestMode.TEXT_MATCH);
    }
}
