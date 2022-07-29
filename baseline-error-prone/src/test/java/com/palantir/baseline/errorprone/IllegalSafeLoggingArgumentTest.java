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
import org.junit.jupiter.api.Timeout;

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
    public void testConstructor() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Unsafe String value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    new Ob(value);",
                        "  }",
                        "  private static final class Ob {",
                        "    Ob(@Safe Object obj) {}",
                        "  }",
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
    public void testUnsafeTypeParameterProvidesSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(List<@Unsafe String> input) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(input.get(0));",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testWildcardTypesAreHandledGracefully() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import one.util.streamex.*;",
                        "import com.google.common.collect.*;",
                        "import java.util.*;",
                        "class Test<K, V> {",
                        "  public Multimap<K, V> asFallthroughValues(Multimap<K, V> values) {",
                        "    return EntryStream.of(values.entries().stream())",
                        "        .collect(Multimaps.toMultimap(",
                        "            Map.Entry::getKey, Map.Entry::getValue, HashMultimap::create));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeTypeParameterProvidesReceiverSafety_iterable() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(List<@Unsafe String> input) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(input);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeTypeParameterProvidesReceiverSafety_map() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(Map<@Unsafe String, @Safe String> one, Map<@Safe String, @Unsafe String> two) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(two);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one.keySet());",
                        "    fun(two.keySet());",
                        "    fun(one.values());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(two.values());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one.entrySet());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(two.entrySet());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one.entrySet().iterator().next().getKey());",
                        "    fun(two.entrySet().iterator().next().getKey());",
                        "    fun(one.entrySet().iterator().next().getValue());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(two.entrySet().iterator().next().getValue());",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeTypeParameterConsumesSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(List<@Safe String> collection, @Unsafe String data) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    collection.add(data);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testUnsafeTypeParameterConsumesSafety_wildcard() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  void f(Consumer<@Safe ? super CharSequence> consumer, @Unsafe String data) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    consumer.accept(data);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testTypeParamsDifferFromBase() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  @Unsafe static class UnsafeClass {}",
                        "  interface Foo<T> extends Iterable<UnsafeClass> {}",
                        "  void f(Foo<@Safe String> foo) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(foo);",
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
    public void testIntegerCompound() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(",
                        "      @Safe int safeParam,",
                        "      @Unsafe int unsafeParam,",
                        "      @DoNotLog int doNotLogParam) {",
                        "    int foo = safeParam;",
                        "    fun(foo);",
                        "    foo += unsafeParam;",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(foo);",
                        "    foo += doNotLogParam;",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(foo);",
                        "  }",
                        "  private static void fun(@Safe int obj) {}",
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
    public void testInvalidReturnValue() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  @Safe Object safe() {",
                        "    return new SafeClass();",
                        "  }",
                        "  @Safe Object unsafe() {",
                        "    // BUG: Diagnostic contains: Dangerous return value: result is 'UNSAFE' "
                                + "but the method is annotated 'SAFE'.",
                        "    return new UnsafeClass();",
                        "  }",
                        "  @Safe Object doNotLog() {",
                        "    // BUG: Diagnostic contains: Dangerous return value: result is 'DO_NOT_LOG' "
                                + "but the method is annotated 'SAFE'.",
                        "    return new DoNotLogClass();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testInvalidReturnValue_AnnotatedSupertype() {
        helper().addSourceLines(
                        "Annotated.java",
                        "import com.palantir.logsafe.*;",
                        "interface Annotated {",
                        "  @Safe Object safe();",
                        "  @Safe Object unsafe();",
                        "  @Safe Object doNotLog();",
                        "}")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test implements Annotated {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  @Override public Object safe() {",
                        "    return new SafeClass();",
                        "  }",
                        "  @Override public Object unsafe() {",
                        "    // BUG: Diagnostic contains: Dangerous return value: result is 'UNSAFE' "
                                + "but the method is annotated 'SAFE'.",
                        "    return new UnsafeClass();",
                        "  }",
                        "  @Override public Object doNotLog() {",
                        "    // BUG: Diagnostic contains: Dangerous return value: result is 'DO_NOT_LOG' "
                                + "but the method is annotated 'SAFE'.",
                        "    return new DoNotLogClass();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testInvalidValue_AnnotatedSupertype() {
        helper().addSourceLines(
                        "Annotated.java",
                        "import com.palantir.logsafe.*;",
                        "interface Annotated {",
                        "  void safe(@Safe Object value);",
                        "}")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test implements Annotated {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    safe(new DoNotLogClass());",
                        "    safe(new SafeClass());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    safe(new UnsafeClass());",
                        "  }",
                        "  @Override public void safe(Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testThrowableMessageIsUnsafe() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(IllegalStateException e) {",
                        "    String message = e.getMessage();",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(message);",
                        "    String localized = e.getLocalizedMessage();",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(localized);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testThrowableMessageInheritsDoNotLogFromThrowable() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@DoNotLog IllegalStateException e) {",
                        "    String message = e.getMessage();",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(message);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testThrowableParameterIsUnsafe() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(IllegalStateException e) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(e);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testThrowableStackTraceAsStringIsUnsafe() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.google.common.base.Throwables;",
                        "class Test {",
                        "  void f(IllegalStateException e) {",
                        "    String strVal = Throwables.getStackTraceAsString(e);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(strVal);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testCatchDoNotLog() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.google.common.base.Throwables;",
                        "class Test {",
                        "  void f() {",
                        "    try {",
                        "      action();",
                        "    } catch (@DoNotLog RuntimeException e) {",
                        "      // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'UNSAFE'.",
                        "      fun(e);",
                        "      // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'UNSAFE'.",
                        "      fun(e.getMessage());",
                        "    }",
                        "  }",
                        "  protected void action() {}",
                        "  private static void fun(@Unsafe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testStringFormat() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Safe String safeParam, @Unsafe String unsafeParam, @DoNotLog String dnlParam) {",
                        "    fun(String.format(\"%s\", safeParam));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(String.format(\"%s\", unsafeParam));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(String.format(\"%s\", dnlParam));",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testArraySafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Safe String safeParam, @Unsafe String unsafeParam, @DoNotLog String dnlParam) {",
                        "    Object[] one = new Object[3];",
                        "    fun(one);",
                        "    one[0] = unsafeParam;",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one);",
                        "    one[1] = dnlParam;",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one);",
                        "    one[2] = safeParam;",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(one);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(new Object[] {safeParam, unsafeParam, dnlParam});",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSwitchExpression() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  enum Nums { ONE, TWO }",
                        "  void f(Nums value) {",
                        "    fun(switch (value) {",
                        "        case ONE -> 1;",
                        "        case TWO -> 2;",
                        "    });",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testPreconditionsPassthrough() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Safe String safe, @Unsafe String unsafe, @DoNotLog String dnl) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(com.google.common.base.Preconditions.checkNotNull(unsafe));",
                        "    fun(com.google.common.base.Preconditions.checkNotNull(safe, dnl));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(com.palantir.logsafe.Preconditions.checkNotNull(unsafe));",
                        "    fun(com.palantir.logsafe.Preconditions.checkNotNull(safe, dnl));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(com.palantir.logsafe.Preconditions.checkArgumentNotNull(unsafe));",
                        "    fun(com.palantir.logsafe.Preconditions.checkArgumentNotNull(safe, dnl));",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testStringFunctions() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Safe String safeParam, @Unsafe String unsafeParam, @DoNotLog String dnlParam) {",
                        "    fun(safeParam.getBytes());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(unsafeParam.getBytes());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(dnlParam.getBytes());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(dnlParam.toLowerCase());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(dnlParam.split(\":\"));",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testImmutableCollectionFactories() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.google.common.collect.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(@Safe String safeParam, @Unsafe String unsafeParam, @DoNotLog String dnlParam) {",
                        "    fun(ImmutableList.of());",
                        "    fun(ImmutableList.of(safeParam));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(ImmutableList.of(unsafeParam));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(ImmutableSet.copyOf(Set.of(unsafeParam)));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(Map.of(safeParam, unsafeParam, \"const\", dnlParam));",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testFieldAnnotation() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Unsafe private static final String SECRET = System.getProperty(\"foo\");",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(SECRET);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testLocalAnnotation() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f() {",
                        "    @DoNotLog String localVar = System.getProperty(\"bar\");",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(localVar);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testLocalAnnotationAssignment() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Safe String safeParam, @Unsafe String unsafeParam, @DoNotLog String dnlParam) {",
                        "    @Safe String local = safeParam;",
                        "    // BUG: Diagnostic contains: Dangerous assignment: value is 'UNSAFE' "
                                + "but the variable is annotated 'SAFE'.",
                        "    local = unsafeParam;",
                        "    // BUG: Diagnostic contains: Dangerous assignment: value is 'DO_NOT_LOG' "
                                + "but the variable is annotated 'SAFE'.",
                        "    local = dnlParam;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testFieldAnnotationAssignment() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Safe private static String field;",
                        "  void f(@Safe String safeParam, @Unsafe String unsafeParam, @DoNotLog String dnlParam) {",
                        "    field = safeParam;",
                        "    // BUG: Diagnostic contains: Dangerous assignment: value is 'UNSAFE' "
                                + "but the variable is annotated 'SAFE'.",
                        "    field = unsafeParam;",
                        "    // BUG: Diagnostic contains: Dangerous assignment: value is 'DO_NOT_LOG' "
                                + "but the variable is annotated 'SAFE'.",
                        "    field = dnlParam;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testThrowableFieldAssignment() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test<OutputT> {",
                        "  private volatile Set<Throwable> fieldValue = null;",
                        "  static final class Other {",
                        "  void f(Test obj, Set<Throwable> newValue) {",
                        "    synchronized (obj) {",
                        "      if (obj.fieldValue != newValue)",
                        "      obj.fieldValue = newValue;",
                        "    }",
                        "  }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void disagreeingSafetyAnnotations() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Safe static class SafeClass {}",
                        "  @Unsafe static class UnsafeClass {}",
                        "  // BUG: Diagnostic contains: Dangerous return type: type is 'DO_NOT_LOG' "
                                + "but the method is annotated 'UNSAFE'.",
                        "  @Unsafe DoNotLogClass f(",
                        "    @Safe SafeClass superSafe,",
                        "    // BUG: Diagnostic contains: Dangerous variable: type is 'UNSAFE' "
                                + "but the variable is annotated 'SAFE'.",
                        "    @Safe UnsafeClass oops",
                        "    ) {",
                        "    return null;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testOptionalUnwrapping() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(@Unsafe Optional<String> p) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(p);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(p.get());",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testOptionalUnsafeType() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f(Optional<UnsafeClass> p) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(p.get());",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testStreamSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.stream.*;",
                        "class Test {",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f(@Unsafe Stream<String> s, @DoNotLog String dnl) {",
                        "    Stream<String> t = s.filter(val -> val.length() > 1).limit(100);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(t.toArray());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(t.collect(Collectors.toSet()));",
                        "    fun(s.count());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(Stream.of(dnl));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(Stream.concat(s, Stream.of(dnl)));",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testOptionalElseSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  @Unsafe static class UnsafeClass {}",
                        "  void f(@Safe Optional<String> s, @Unsafe String unsafe) {",
                        "    fun(s);",
                        "    fun(s.get());",
                        "    fun(s.orElseThrow(RuntimeException::new));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(s.orElse(unsafe));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(Optional.ofNullable(unsafe).orElse(s.get()));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(Optional.ofNullable(unsafe).stream());",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testEnhancedForLoopSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  void f(@Unsafe List<String> strings) {",
                        "    for (String s : strings) {",
                        "      // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "      fun(s);",
                        "    }",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testRawTypes() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "@SuppressWarnings(\"rawtypes\")",
                        "interface Test<T extends CharSequence> {",
                        "  Iface iface();",
                        "  default void f(@Unsafe String value) {",
                        "    iface().accept(value);",
                        "  }",
                        "  interface Iface<T> extends Consumer<T> {}",
                        "}")
                .doTest();
    }

    @Test
    public void testResourceIdentifierComponentSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "import com.palantir.ri.*;",
                        "class Test {",
                        "  void f(@Unsafe ResourceIdentifier rid) {",
                        "    fun(rid.getService());",
                        "    fun(rid.getInstance());",
                        "    fun(rid.getType());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(rid.getLocator());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(rid);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    fun(rid.toString());",
                        "  }",
                        "  void fun(@Safe Object in) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testResourceIdentifierCreationSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "import com.palantir.ri.*;",
                        "class Test {",
                        "  void f(@Unsafe String value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    safe(ResourceIdentifier.of(value));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    safe(ResourceIdentifier.valueOf(value));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    safe(ResourceIdentifier.of(\"service\", \"instance\", \"type\", value));",
                        "    ResourceIdentifier varArgs = ResourceIdentifier.of(",
                        "      \"service\", \"instance\", \"type\", \"loc\", value);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE' "
                                + "but the parameter requires 'SAFE'.",
                        "    safe(varArgs);",
                        "    safe(ResourceIdentifier.valueOf(\"ri.service.instance.type.name\"));",
                        "    safe(ResourceIdentifier.of(\"ri.service.instance.type.name\"));",
                        "    safe(ResourceIdentifier.of(\"service\", \"instance\", \"type\", \"safe-locator\"));",
                        "    safe(ResourceIdentifier.of(\"service\", \"instance\", \"type\", \"safe\", \"locator\"));",
                        "  }",
                        "  void safe(@Safe Object in) {}",
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

    @Test
    public void testBinaryOperationsOnUnsafeInput() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Safe",
                        "  void f(@Unsafe Throwable input) {",
                        "    fun(input instanceof InterruptedException);",
                        "    fun(input == null);",
                        "    fun(input != null);",
                        "    fun(input == new Object());",
                        "    fun(input != null && input instanceof InterruptedException);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testPrimitiveBoxing() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Safe",
                        "  void f(@Unsafe long input) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(input);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testPrimitiveUnboxing() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Safe",
                        "  void f(@Unsafe Long input) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(input);",
                        "  }",
                        "  private static void fun(@Safe long value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testStringBuilder() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Safe String safe, @Unsafe String unsafe) {",
                        "    fun(new StringBuilder(safe));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(new StringBuilder(unsafe));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(new StringBuilder(safe).append(unsafe).toString());",
                        "    StringBuilder sb = new StringBuilder().append(safe);",
                        "    sb.append(safe).append(unsafe);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(sb.append(safe).toString());",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testStringBuffer() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  void f(@Safe String safe, @Unsafe String unsafe) {",
                        "    fun(new StringBuffer(safe));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(new StringBuffer(unsafe));",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(new StringBuffer(safe).append(unsafe).toString());",
                        "    StringBuffer sb = new StringBuffer().append(safe);",
                        "    sb.append(safe).append(unsafe);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(sb.append(safe).toString());",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testTypeVariable() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test<@Unsafe T> {",
                        "  void f(T value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(value);",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testBindingToSafeTypeVariable() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Unsafe static final class UnsafeType {}",
                        "  static final class One<@Safe T> {",
                        "    static <U> One<U> of(U value) { return null; }",
                        "  }",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    new One<UnsafeType>();",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    One.of(new UnsafeType());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testMethodTypeVariable() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  private static void fun(@Safe Object value) {}",
                        "  private static <@Unsafe T> void handle(T input) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(input);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testBindingToSafeMethodTypeVariable() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.Optional;",
                        "class Test {",
                        "  @Unsafe static final class UnsafeType {}",
                        "  static <@Safe U> Optional<U> of(U value) { return Optional.empty(); }",
                        "  static <@Safe U> Optional<U> empty() { return Optional.empty(); }",
                        "  void f() {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    Test.of(new UnsafeType());",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    Test.<UnsafeType>empty();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testTypeVariablesInConstructor() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  static final class Foo<T> {",
                        "    Foo(T input) {}",
                        "  }",
                        "  static Foo<String> f(Supplier<String> value) {",
                        "    return new Foo<>(value.get());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testRecordConstruction() {
        helper().setArgs("--release", "17")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  record MyRecord(@Safe String value) {}",
                        "  void f(@Unsafe String value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    new MyRecord(value);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testRecordComponentUsage() {
        helper().setArgs("--release", "17")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  record MyRecord(@Unsafe String value) {}",
                        "  void f(MyRecord rec) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(rec.value());",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testTransformingStreamCollectors() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "import java.util.function.*;",
                        "import java.util.stream.*;",
                        "import com.google.common.collect.*;",
                        "class Test {",
                        "  @DoNotLog",
                        "  interface TopLevel {",
                        "     @Safe String name();",
                        "     @Safe String value();",
                        "     @DoNotLog String token();",
                        "  }",
                        "  void f(@Unsafe Stream<TopLevel> stream) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(stream.collect(Collectors.toList()));",
                        "    fun(stream.collect(Collectors.counting()));",
                        "    fun(stream.collect(Collectors.toMap(TopLevel::name, TopLevel::value)));",
                        "    fun(stream.collect(Collectors.toConcurrentMap(TopLevel::name, TopLevel::value)));",
                        "    fun(stream.collect(Collectors.toUnmodifiableMap(TopLevel::name, TopLevel::value)));",
                        "    fun(stream.collect(ImmutableMap.toImmutableMap(TopLevel::name, TopLevel::value)));",
                        "    fun(stream.collect(ImmutableBiMap.toImmutableBiMap(TopLevel::name, TopLevel::value)));",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testResultOfInvocationSuperInterfaceAnnotated() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  @Unsafe",
                        "  interface Iface {",
                        "  }",
                        "  static final class Impl implements Iface {}",
                        "  void f(Supplier<Impl> supplier) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(supplier.get());",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSuperClassInterfaceAnnotated() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Unsafe",
                        "  interface Iface {",
                        "  }",
                        "  static class Sup implements Iface {}",
                        "  static class Sub extends Sup {}",
                        "  void f(Sub value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(value);",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testMultipleInterfacesDifferentSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Unsafe interface UnsafeIface {}",
                        "  @Safe interface SafeIface {}",
                        "  @DoNotLog interface DnlIface {}",
                        "  static class One implements SafeIface, UnsafeIface {}",
                        "  static class Two implements SafeIface, DnlIface, UnsafeIface {}",
                        "  void f(One one, Two two) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun(one);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(two);",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSuperclassLessStrictThanInterfaces() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog interface DnlIface {}",
                        "  @Safe static class Sup {}",
                        "  static class Impl extends Sup implements DnlIface {}",
                        "  void f(Impl value) {",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun(value);",
                        "  }",
                        "  private static void fun(@Safe Object value) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testCastSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog class DoNotLogClass {}",
                        "  @Safe interface SafeClass {}",
                        "  @Unsafe interface UnsafeClass {}",
                        "  void f(Object object, UnsafeClass unsafeObject, @Unsafe Object unsafeAnnotatedObject) {",
                        "    fun(object);",
                        "    fun((SafeClass) object);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun((UnsafeClass) object);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'DO_NOT_LOG'",
                        "    fun((DoNotLogClass) object);",
                        "    // BUG: Diagnostic contains: Dangerous argument value: arg is 'UNSAFE'",
                        "    fun((SafeClass) unsafeObject);",
                        "    fun((SafeClass) unsafeAnnotatedObject);",
                        "  }",
                        "  private static void fun(@Safe Object obj) {}",
                        "}")
                .doTest();
    }

    @Test
    public void testSubclassWithLenientSafety() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Unsafe interface UnsafeClass {}",
                        "  // BUG: Diagnostic contains: "
                                + "Dangerous type: annotated 'SAFE' but ancestors declare 'UNSAFE'.",
                        "  @Safe interface SafeSubclass extends UnsafeClass {}",
                        "}")
                .doTest();
    }

    @Test
    @Timeout(10) // https://github.com/palantir/gradle-baseline/issues/2328
    public void testPatternMatching() {
        helper().setArgs("--release", "17")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog static class DoNotLogClass {}",
                        "  @Unsafe Object f(Object value) {",
                        "    if (value instanceof Integer i) {",
                        "        return i;",
                        "    } else if (value instanceof Float f) {",
                        "        return f;",
                        "    } else if (value instanceof Double d) {",
                        "        return d;",
                        "    } else if (value instanceof Byte b) {",
                        "        return b;",
                        "    } else if (value instanceof CharSequence cs) {",
                        "        return cs;",
                        "    } else if (value instanceof String str) {",
                        "        return str;",
                        "    } else if (value instanceof Throwable t) {",
                        "        return t;",
                        "    } else if (value instanceof DoNotLogClass dnl) {",
                        "        // BUG: Diagnostic contains: Dangerous return value: result is 'DO_NOT_LOG'",
                        "        return dnl;",
                        "    }",
                        "    return null;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testPatternMatchingUnsafeToSafeSubtype() {
        helper().setArgs("--release", "17")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Safe static class SafeClass {}",
                        "  @Safe Object f(@Unsafe Object value) {",
                        "    if (value instanceof SafeClass s) {",
                        "        return s;",
                        "    }",
                        "    return null;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testPatternMatchingUnsafeToUnknown() {
        helper().setArgs("--release", "17")
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @Safe static class SafeClass {}",
                        "  @Safe Object f(@Unsafe Object value) {",
                        "    if (value instanceof String s) {",
                        "        // BUG: Diagnostic contains: Dangerous return value: result is 'UNSAFE'",
                        "        return s;",
                        "    }",
                        "    return null;",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(IllegalSafeLoggingArgument.class, getClass());
    }

    private RefactoringValidator refactoringHelper() {
        return RefactoringValidator.of(IllegalSafeLoggingArgument.class, getClass());
    }
}
