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

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(IllegalSafeLoggingArgument.class, getClass());
    }
}
