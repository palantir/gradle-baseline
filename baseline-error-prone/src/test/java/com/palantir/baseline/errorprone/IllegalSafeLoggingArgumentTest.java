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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class IllegalSafeLoggingArgumentTest {

    @Test
    public void testNonAnnotatedParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    fun(new DoNotLogClass());",
                        "    fun(DoNotLogClass.class);",
                        "    fun(new SafeClass());",
                        "    fun(SafeClass.class);",
                        "    fun(new UnsafeClass());",
                        "    fun(UnsafeClass.class);",
                        "  }",
                        "  private static void fun(Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testDoNotLogParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    fun(new DoNotLogClass());",
                        "    fun(DoNotLogClass.class);",
                        "    fun(new SafeClass());",
                        "    fun(SafeClass.class);",
                        "    fun(new UnsafeClass());",
                        "    fun(UnsafeClass.class);",
                        "  }",
                        "  private static void fun(@DoNotLog Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'UNSAFE'.",
                        "    fun(new DoNotLogClass());",
                        "    fun(DoNotLogClass.class);",
                        "    fun(new SafeClass());",
                        "    fun(SafeClass.class);",
                        "    fun(new UnsafeClass());",
                        "    fun(UnsafeClass.class);",
                        "  }",
                        "  private static void fun(@Unsafe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSafeParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(new DoNotLogClass());",
                        "    fun(DoNotLogClass.class);",
                        "    fun(new SafeClass());",
                        "    fun(SafeClass.class);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(new UnsafeClass());",
                        "    fun(UnsafeClass.class);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testVarArgParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    fun(",
                        "        // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "        new DoNotLogClass(),",
                        "        DoNotLogClass.class,",
                        "        new SafeClass(),",
                        "        SafeClass.class,",
                        "        // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "        new UnsafeClass(),",
                        "        UnsafeClass.class);",
                        "  }",
                        "  private static void fun(@Safe Object... obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testDoNotLogReturn() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  void f() {",
                        "    fun(doNotLogMethod());",
                        "    fun(safeMethod());",
                        "    fun(unsafeMethod());",
                        "  }",
                        "  private static void fun(@DoNotLog Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeReturn() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'UNSAFE'.",
                        "    fun(doNotLogMethod());",
                        "    fun(safeMethod());",
                        "    fun(unsafeMethod());",
                        "  }",
                        "  private static void fun(@Unsafe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSafeReturn() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(doNotLogMethod());",
                        "    fun(safeMethod());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(unsafeMethod());",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSafeReturnIndirect() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  void f() {",
                        "    Object one = doNotLogMethod();",
                        "    Object two = safeMethod();",
                        "    Object three = unsafeMethod();",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one);",
                        "    fun(two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(three);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testStringConcatenationWithUnsafe() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  void f() {",
                        "    Object one = doNotLogMethod();",
                        "    Object two = safeMethod();",
                        "    Object three = unsafeMethod();",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(\"test\" + one);",
                        "    fun(\"test\" + two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(\"test\" + three);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testVarArgReturn() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  void f() {",
                        "    fun(",
                        "        // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "        doNotLogMethod(),",
                        "        safeMethod(),",
                        "        // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "        unsafeMethod());",
                        "  }",
                        "  private static void fun(@Safe Object... obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testIncomingParameter() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(",
                        "      @Safe Object safeParam,",
                        "      @Unsafe Object unsafeParam,",
                        "      @DoNotLog Object doNotLogParam) {",
                        "    fun(safeParam);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(unsafeParam);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(doNotLogParam);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testTypes_toStringUsesObjectSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    fun(new SafeClass().toString());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(new UnsafeClass().toString());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(new DoNotLogClass().toString());",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeFinalFieldInitializer() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  public final Object one = doNotLogMethod();",
                        "  public final Object two = safeMethod();",
                        "  public final Object three = unsafeMethod();",
                        "  void f() {",
                        "    fun(two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(three);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(one);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeFieldInitializer() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static Object doNotLogMethod() { return null; }",
                        "  @Safe static Object safeMethod() { return null; }",
                        "  @Unsafe static Object unsafeMethod() { return null; }",
                        "  public Object one = doNotLogMethod();",
                        "  public Object two = safeMethod();",
                        "  public Object three = unsafeMethod();",
                        "  void f() {",
                        "    fun(two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(three);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(one);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeFieldType() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  public DoNotLogClass one;",
                        "  public SafeClass two;",
                        "  public UnsafeClass three;",
                        "  void f() {",
                        "    fun(two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(three);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(one);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeMethodReturnType_retainsUnsafeType() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  static DoNotLogClass doNotLogMethod() { return new DoNotLogClass(); }",
                        "  static SafeClass safeMethod() { return new SafeClass(); }",
                        "  static UnsafeClass unsafeMethod() { return new UnsafeClass(); }",
                        "  void f() {",
                        "    DoNotLogClass one = doNotLogMethod();",
                        "    SafeClass two = safeMethod();",
                        "    UnsafeClass three = unsafeMethod();",
                        "    fun(two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(three);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(one);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeMethodReturnType_widenedType() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  static DoNotLogClass doNotLogMethod() { return new DoNotLogClass(); }",
                        "  static SafeClass safeMethod() { return new SafeClass(); }",
                        "  static UnsafeClass unsafeMethod() { return new UnsafeClass(); }",
                        "  void f() {",
                        "    Object one = doNotLogMethod();",
                        "    Object two = safeMethod();",
                        "    Object three = unsafeMethod();",
                        "    fun(two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(three);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(one);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testIncomingParameter_toStringUsesObjectSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(",
                        "      @Safe Object safeParam,",
                        "      @Unsafe Object unsafeParam,",
                        "      @DoNotLog Object doNotLogParam) {",
                        "    fun(safeParam.toString());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(unsafeParam.toString());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(doNotLogParam.toString());",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testStringConcatAssignmentSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(",
                        "      @Safe String safeParam,",
                        "      @Unsafe String unsafeParam,",
                        "      @DoNotLog String doNotLogParam) {",
                        "    String foo = safeParam;",
                        "    fun(foo);",
                        "    foo += unsafeParam;",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(foo);",
                        "    foo += doNotLogParam;",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(foo);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSafeOfThisUnsafe() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "@Unsafe",
                        "class Test {",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(this);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSafeOfSuperToStringUnsafe() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "@Unsafe",
                        "class Test {",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(super.toString());",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSafeArgOfUnsafe_recommendsUnsafeArgOf() {
        refactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.Unsafe;",
                        "class Test {",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    SafeArg.of(\"unsafe\", new UnsafeClass());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.Unsafe;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "class Test {",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    UnsafeArg.of(\"unsafe\", new UnsafeClass());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testSafeArgOfDoNotLog_noRecommendation() {
        refactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.DoNotLog;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    SafeArg.of(\"unsafe\", new DoNotLogClass());",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    public void testUnsafeArgumentForSafeParameter_noRecommendation() {
        refactoringHelper()
                .addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.Safe;",
                        "import com.palantir.logsafe.Unsafe;",
                        "class Test {",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(new UnsafeClass());",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(IllegalSafeLoggingArgument.class, getClass());
    }

    private RefactoringValidator refactoringHelper() {
        return RefactoringValidator.of(IllegalSafeLoggingArgument.class, getClass());
    }
}
