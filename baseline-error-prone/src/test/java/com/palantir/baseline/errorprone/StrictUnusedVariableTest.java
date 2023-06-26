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
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

public class StrictUnusedVariableTest {

    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringTestHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(StrictUnusedVariable.class, getClass());
        refactoringTestHelper = RefactoringValidator.of(StrictUnusedVariable.class, getClass());
    }

    @Test
    public void handles_interface() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "interface Test {",
                        "  void method(String param);",
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
                        "  // BUG: Diagnostic contains: 'buggy' is never read",
                        "  private static void privateMethod(String buggy) { }",
                        "  // BUG: Diagnostic contains: 'buggy' is never read",
                        "  public static void publicMethod(String buggy) { }",
                        "}")
                .doTest();
    }

    @Test
    void handles_lambdas() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.function.BiFunction;",
                        "import java.util.Optional;",
                        "class Test {",
                        "  private static BiFunction<String, String, Integer> doStuff() {",
                        "  // BUG: Diagnostic contains: 'value1' is never read",
                        "    BiFunction<String, String, Integer> first = (String value1, String value2) -> 1;",
                        "  // BUG: Diagnostic contains: 'value3' is never read",
                        "    return first.andThen(value3 -> 2);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void handles_lambdas_in_static_init() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.function.BiFunction;",
                        "class Test {",
                        "  static {",
                        "  // BUG: Diagnostic contains: 'value1' is never read",
                        "    BiFunction<String, String, Integer> first = (String value1, String value2) -> 1;",
                        "  // BUG: Diagnostic contains: 'value3' is never read",
                        "    first.andThen(value3 -> 2);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void renames_previous_suppression() {
        // if it is prefixed with 'unused' don't do anything.
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: 'unusedValue' is never read",
                        "  public void publicMethod(String unusedValue, String unusedValue2) { }",
                        "  // BUG: Diagnostic contains: 'unusedValue' is never read",
                        "  public void varArgs(String unusedValue, String... unusedValue2) { }",
                        "}")
                .doTest();
    }

    @Test
    public void renames_unused_param() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: 'value' is never read",
                        "  private void privateMethod(String value) { }",
                        "  // BUG: Diagnostic contains: 'value' is never read",
                        "  public void publicMethod(String value, String value2) { }",
                        "  // BUG: Diagnostic contains: 'value' is never read",
                        "  public void varArgs(String value, String... value2) { }",
                        "}")
                .doTest();
    }

    @Test
    void renames_unused_lambda_params() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.function.BiFunction;",
                        "class Test {",
                        "  private static BiFunction<String, String, Integer> doStuff() {",
                        "  // BUG: Diagnostic contains: 'value1' is never read.",
                        "    BiFunction<String, String, Integer> first = (String value1, String value2) -> 1;",
                        "  // BUG: Diagnostic contains: 'value3' is never read.",
                        "    return first.andThen(value3 -> 2);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void renames_used_lambda_params() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.IntStream;",
                        "import java.util.stream.Stream;",
                        "",
                        "public final class Test {",
                        "    private Test() {}",
                        "    private static String randomEvent() { return null; }",
                        "    public static List<?> work() {",
                        "        return IntStream.iterate(0, _i -> _i + 1).mapToObj(_i -> randomEvent())",
                        "                .limit(1)",
                        "                .collect(Collectors.toList());",
                        "    }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.IntStream;",
                        "import java.util.stream.Stream;",
                        "",
                        "public final class Test {",
                        "    private Test() {}",
                        "    private static String randomEvent() { return null; }",
                        "    public static List<?> work() {",
                        "        return IntStream.iterate(0, i -> i + 1).mapToObj(_i -> randomEvent())",
                        "                .limit(1)",
                        "                .collect(Collectors.toList());",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_suppressed_but_used_variables() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: '_field' is read",
                        "  private static final String _field = \"\";",
                        "  // BUG: Diagnostic contains: '_value' is read",
                        "  public static void privateMethod(String _value) {",
                        "    System.out.println(_value);",
                        "  // BUG: Diagnostic contains: '_bar' is read",
                        "    String _bar = \"bar\";",
                        "    System.out.println(_bar);",
                        "    System.out.println(_field);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void side_effects_are_preserved() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  private static int _field = 1;",
                        "  public static void privateMethod() {",
                        "  // BUG: Diagnostic contains: 'foo' is never read",
                        "    Object foo = someMethod();",
                        "  }",
                        "  private static Object someMethod() { return null; }",
                        "}")
                .doTest();
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
    @DisabledForJreRange(max = JRE.JAVA_16)
    public void testRecord() {
        compilationHelper = CompilationTestHelper.newInstance(StrictUnusedVariable.class, getClass())
                .setArgs("--release", "17");

        compilationHelper
                .addSourceLines("Test.java", "class Test {", "  record Foo(int bar) {}", "}")
                .doTest();
    }

    @Test
    public void testParameterSuppression() {
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

    @Test
    public void testMethodSuppression() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  @SuppressWarnings(\"StrictUnusedVariable\")",
                        "  public static void a(int val) {}",
                        "  @SuppressWarnings(\"UnusedVariable\")",
                        "  public static void b(int val) {}",
                        "  @SuppressWarnings(\"unused\")",
                        "  public static void c(int val) {}",
                        "  public static void d(int _val) {}",
                        "}")
                .expectUnchanged()
                .doTest(TestMode.TEXT_MATCH);
    }

    @Test
    public void testClassSuppression() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  @SuppressWarnings(\"StrictUnusedVariable\")",
                        "  class Test1 {",
                        "    public static void a(int val) {}",
                        "  }",
                        "  @SuppressWarnings(\"UnusedVariable\")",
                        "  class Test2 {",
                        "    public static void a(int val) {}",
                        "  }",
                        "  @SuppressWarnings(\"unused\")",
                        "  class Test3 {",
                        "    public static void a(int val) {}",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(TestMode.TEXT_MATCH);
    }

    @Test
    public void allows_unused_loggers() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.slf4j.*;",
                        "import com.palantir.logsafe.logger.*;",
                        "class Test {",
                        "  private static final Logger slf4j = LoggerFactory.getLogger(Test.class);",
                        "  private static final SafeLogger logsafe = SafeLoggerFactory.get(Test.class);",
                        "  // BUG: Diagnostic contains: 'str' is never read",
                        "  private static final String str = \"str\";",
                        "}")
                .doTest();
    }
}
