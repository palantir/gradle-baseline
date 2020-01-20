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
class RedundantModifierTest {

    @Test
    void fixEnumConstructor() {
        fix().addInputLines(
                        "Test.java",
                        "public enum Test {",
                        "  INSTANCE(\"str\");",
                        "  private final String str;",
                        "  private Test(String str) {",
                        "    this.str = str;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public enum Test {",
                        "  INSTANCE(\"str\");",
                        "  private final String str;",
                        "  Test(String str) {",
                        "    this.str = str;",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void allowEnumResult() {
        helper().addSourceLines(
                        "Test.java",
                        "public enum Test {",
                        "  INSTANCE(\"str\");",
                        "  private final String str;",
                        "  Test(String str) {",
                        "    this.str = str;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    void fixStaticEnum() {
        fix().addInputLines(
                        "Enclosing.java",
                        "public class Enclosing {",
                        "  public static enum Test {",
                        "    INSTANCE",
                        "  }",
                        "}")
                .addOutputLines(
                        "Enclosing.java",
                        "public class Enclosing {",
                        "  public enum Test {",
                        "    INSTANCE",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testAllowPrivateEnum() {
        helper().addSourceLines(
                        "Enclosing.java",
                        "public class Enclosing {",
                        "  private enum Test {",
                        "    INSTANCE",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAllowInterfaceNestedEnum() {
        helper().addSourceLines(
                        "Enclosing.java", "public interface Enclosing {", "  enum Test {", "    INSTANCE", "  }", "}")
                .doTest();
    }

    @Test
    void fixStaticInterface() {
        fix().addInputLines(
                        "Enclosing.java", "public class Enclosing {", "  public static interface Test {", "  }", "}")
                .addOutputLines("Enclosing.java", "public class Enclosing {", "  public interface Test {", "  }", "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void allowInterface() {
        helper().addSourceLines("Enclosing.java", "public class Enclosing {", "  public interface Test {", "  }", "}")
                .doTest();
    }

    @Test
    void allowNestedInterface() {
        helper().addSourceLines("Enclosing.java", "interface Enclosing {", "  interface Test {", "  }", "}")
                .doTest();
    }

    @Test
    void fixInterfaceMethods() {
        fix().addInputLines(
                        "Enclosing.java",
                        "public class Enclosing {",
                        "  public interface Test {",
                        "    public int a();",
                        "    int b();",
                        "    abstract int c();",
                        "    public abstract int d();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Enclosing.java",
                        "public class Enclosing {",
                        "  public interface Test {",
                        "    int a();",
                        "    int b();",
                        "    int c();",
                        "    int d();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void allowValidInterfaceMethods() {
        helper().addSourceLines(
                        "Enclosing.java",
                        "public class Enclosing {",
                        "  public interface Test {",
                        "    int a();",
                        "    int b();",
                        "    int c();",
                        "    int d();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void fixFinalClassModifiers() {
        fix().addInputLines(
                        "Test.java",
                        "public final class Test {",
                        "  public final void a() {}",
                        "  private final void b() {}",
                        "  final void c() {}",
                        "  @SafeVarargs public final void d(Object... value) {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public final class Test {",
                        "  public void a() {}",
                        "  private void b() {}",
                        "  void c() {}",
                        // SafeVarargs is a special case
                        "  @SafeVarargs public final void d(Object... value) {}",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void allowFinalClass() {
        helper().addSourceLines(
                        "Test.java",
                        "public final class Test {",
                        "  public void a() {}",
                        "  private void b() {}",
                        "  void c() {}",
                        // SafeVarargs is a special case
                        "  @SafeVarargs public final void d(Object... value) {}",
                        "}")
                .doTest();
    }

    @Test
    void fixStaticFinalMethod() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static final int a() {",
                        "    return 1;",
                        "  }",
                        "  private static final int b() {",
                        "    return 1;",
                        "  }",
                        "  static final int c() {",
                        "    return 1;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  public static int a() {",
                        "    return 1;",
                        "  }",
                        "  private static int b() {",
                        "    return 1;",
                        "  }",
                        "  static int c() {",
                        "    return 1;",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void allowStaticMethods() {
        helper().addSourceLines(
                        "Test.java",
                        "public class Test {",
                        "  public static int a() {",
                        "    return 1;",
                        "  }",
                        "  private static int b() {",
                        "    return 1;",
                        "  }",
                        "  static int c() {",
                        "    return 1;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void fixInterfacePublicStaticMethod() {
        fix().addInputLines("Test.java", "public interface Test {", "  public static void a() {}", "}")
                .addOutputLines("Test.java", "public interface Test {", "  static void a() {}", "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void fixInterfaceStaticFieldModifiers() {
        fix().addInputLines(
                        "Test.java",
                        "public interface Test {",
                        "  int VALUE_0 = 0;",
                        "  public static final int VALUE_1 = 1;",
                        "  public final int VALUE_2 = 2;",
                        "  public static int VALUE_3 = 3;",
                        "  final int VALUE_4 = 4;",
                        "  static int VALUE_5 = 5;",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public interface Test {",
                        "  int VALUE_0 = 0;",
                        "  int VALUE_1 = 1;",
                        "  int VALUE_2 = 2;",
                        "  int VALUE_3 = 3;",
                        "  int VALUE_4 = 4;",
                        "  int VALUE_5 = 5;",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void fixInterfaceNestedPublicClass() {
        fix().addInputLines(
                        "Test.java",
                        "public interface Test {",
                        "  public static final class Class0 {}",
                        "  public class Class1 {}",
                        "  static class Class2 {}",
                        "  final class Class3 {}",
                        "  public interface Interface0 {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public interface Test {",
                        "  final class Class0 {}",
                        "  class Class1 {}",
                        "  class Class2 {}",
                        "  final class Class3 {}",
                        "  interface Interface0 {}",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new RedundantModifier(), getClass());
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(RedundantModifier.class, getClass());
    }
}
