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
                        "  // BUG: Diagnostic contains: The public method parameter 'param' is never read. Acknowledge"
                            + " an intentional occurrence by renaming the unused variable with a leading underscore,"
                            + " or remove the parameter.\t",
                        "  void defaultMethod(String param) { }",
                        "  // BUG: Diagnostic contains: The parameter 'param' is never read.\t",
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
                        "  // BUG: Diagnostic contains: The public method parameter 'foo' is never read. Acknowledge"
                            + " an intentional occurrence by renaming the unused variable with a leading underscore,"
                            + " or remove the parameter.\t",
                        "   Test(String foo) { }",
                        "  // BUG: Diagnostic contains: The parameter 'buggy' is never read.\t",
                        "  private static void privateMethod(String buggy) { }",
                        "  // BUG: Diagnostic contains: The public method parameter 'buggy' is never read. Acknowledge"
                            + " an intentional occurrence by renaming the unused variable with a leading underscore,"
                            + " or remove the parameter.\t",
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
                        "  // BUG: Diagnostic contains: The parameter 'buggy' is never read.\t",
                        "  private static void privateMethod(String buggy) { }",
                        "  // BUG: Diagnostic contains: The public method parameter 'buggy' is never read. Acknowledge"
                            + " an intentional occurrence by renaming the unused variable with a leading underscore,"
                            + " or remove the parameter.\t",
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
                        "    // BUG: Diagnostic contains: Acknowledge an intentional occurrence by renaming the unused"
                                + " parameter with a leading underscore, or remove the parameter.\t",
                        "    BiFunction<String, String, Integer> first = (String value1, String value2) -> 1;",
                        "    // BUG: Diagnostic contains: The lambda parameter 'value3' is never read. Acknowledge an"
                            + " intentional occurrence by renaming the unused parameter with a leading underscore, or"
                            + " remove the parameter.\t",
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
                        "    // BUG: Diagnostic contains: Acknowledge an intentional occurrence by renaming the unused"
                                + " parameter with a leading underscore, or remove the parameter.\t",
                        "    BiFunction<String, String, Integer> first = (String value1, String value2) -> 1;",
                        "    // BUG: Diagnostic contains: The lambda parameter 'value3' is never read. Acknowledge an"
                            + " intentional occurrence by renaming the unused parameter with a leading underscore, or"
                            + " remove the parameter.\t",
                        "    first.andThen(value3 -> 2);",
                        "  }",
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
    void renames_unused_lambda_params() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "import java.util.function.BiFunction;",
                        "class Test {",
                        "  private static BiFunction<String, String, Integer> doStuff() {",
                        "    BiFunction<String, String, Integer> first = (String value1, String value2) -> 1;",
                        "    return first.andThen(value3 -> 2);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.function.BiFunction;",
                        "class Test {",
                        "  private static BiFunction<String, String, Integer> doStuff() {",
                        "    BiFunction<String, String, Integer> first = (String _value1, String _value2) -> 1;",
                        "    return first.andThen(_value3 -> 2);",
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
    public void handles_unused_variables() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: The field 'field' is never read.\t",
                        "  private static final String field = \"\";",
                        "  // BUG: Diagnostic contains: The parameter 'value' is never read.\t",
                        "  private void privateMethod(String value) {",
                        "    // BUG: Diagnostic contains: The local variable 'bar' is never read.\t",
                        "    String bar = \"bar\";",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_unused_variables() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  private static final String field = \"\";",
                        "  private void privateMethod(String value) {",
                        "    String bar = \"bar\";",
                        "  }",
                        "}")
                .addOutputLines("Test.java", "class Test {", "  private void privateMethod() {", "  }", "}")
                .doTest();
    }

    @Test
    public void handles_unused_local_variable_assignment() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  private void privateMethod() {",
                        "    // BUG: Diagnostic contains: The assignment to the local variable 'bar' is never read.\t",
                        "    String bar = \"bar\";",
                        "    bar = \"foo\";",
                        "    System.out.println(bar);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fixes_unused_local_variable_assignment() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "  private void privateMethod() {",
                        "    String bar = \"bar\";",
                        "    bar = \"foo\";",
                        "    System.out.println(bar);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  private void privateMethod() {",
                        "",
                        "    String bar = \"foo\";",
                        "    System.out.println(bar);",
                        "  }",
                        "}")
                .doTest(TestMode.TEXT_MATCH);
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
    public void fails_suppressed_but_used_variables() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: The field '_field' is read but has 'StrictUnusedVariable'"
                                + " suppressed because of its name.\t",
                        "  private static final String _field = \"\";",
                        "  // BUG: Diagnostic contains: The parameter '_value' is read but has 'StrictUnusedVariable'"
                                + " suppressed because of its name.\t",
                        "  public static void privateMethod(String _value) {",
                        "    System.out.println(_value);",
                        "  // BUG: Diagnostic contains: The local variable '_bar' is read but has"
                                + " 'StrictUnusedVariable' suppressed because of its name.\t",
                        "    String _bar = \"bar\";",
                        "    System.out.println(_bar);",
                        "    System.out.println(_field);",
                        "  }",
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
    public void fixes_previously_suppressed_variables() {
        refactoringTestHelper
                .addInputLines(
                        "Test.java", "class Test {", "  public static void privateMethod(int unused) {", "  }", "}")
                .addOutputLines(
                        "Test.java", "class Test {", "  public static void privateMethod(int _value) {", "  }", "}")
                .doTest(TestMode.TEXT_MATCH);
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
                        "  // BUG: Diagnostic contains: The field 'str' is never read.\t",
                        "  private static final String str = \"str\";",
                        "}")
                .doTest();
    }
}
