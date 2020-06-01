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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class LambdaMethodReferenceTest {

    private CompilationTestHelper compile() {
        return CompilationTestHelper.newInstance(LambdaMethodReference.class, getClass());
    }

    private RefactoringValidator refactor() {
        return RefactoringValidator.of(new LambdaMethodReference(), getClass());
    }

    @Test
    public void testMethodReference() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testInstanceMethod() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<Integer> foo(Optional<String> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.map(v -> v.length());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testLocalInstanceMethod() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<Integer> foo(Optional<String> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.map(v -> bar(v));",
                        "  }",
                        "  private Integer bar(String value) {",
                        "    return value.length();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testLocalInstanceMethodSupplier() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.orElseGet(() -> bar());",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testLocalStaticMethod_multiParam() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + Map.class.getName() + ';',
                        "class Test {",
                        "  public void foo(Map<String, String> map) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    map.forEach((k, v) -> bar(k, v));",
                        "  }",
                        "  private static void bar(String key, String value) {",
                        "    System.out.println(key + value);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testLocalMethodSupplier_block() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.orElseGet(() -> { return bar(); });",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testStaticMethod_block() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.orElseGet(() -> { return ImmutableList.of(); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_staticMethod_block() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> { return ImmutableList.of(); });",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testAutoFix_staticMethodWithParam() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<List<Object>> foo(Optional<Object> optional) {",
                        "    return optional.map(v -> ImmutableList.of(v));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<List<Object>> foo(Optional<Object> optional) {",
                        "    return optional.map(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testAutoFix_InstanceMethod() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<Integer> foo(Optional<String> optional) {",
                        "    return optional.map(v -> v.length());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<Integer> foo(Optional<String> optional) {",
                        "    return optional.map(String::length);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testNegative_InstanceMethodWithType() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<Integer> foo(Optional<String> optional) {",
                        "    return optional.map((String v) -> v.length());",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testNegative_ambiguousStaticReference() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<Long> optional) {",
                        "    return optional.map(value -> Long.toString(value));",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testNegative_ambiguousInstanceReference() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<Long> optional) {",
                        "    return optional.map(value -> value.toString());",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testNegative_ambiguousThis() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Supplier.class.getName() + ';',
                        "class Test {",
                        "  class Inner {",
                        "    public Supplier<String> foo() {",
                        // this::bar is incorrect because 'this' is Inner and 'bar' is defined on 'Test'.
                        "      return () -> bar();",
                        "    }",
                        "  }",
                        "  private String bar() {",
                        "    return \"\";",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testAutoFix_SpecificInstanceMethod() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<Test> optional) {",
                        "    return optional.map(v -> v.toString());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<Test> optional) {",
                        "    return optional.map(Test::toString);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAutoFix_SpecificInstanceMethod_withTypeParameters() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<Optional<Test>> optional) {",
                        "    return optional.map(v -> v.toString());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<Optional<Test>> optional) {",
                        "    return optional.map(Optional::toString);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAutoFix_localInstanceMethod() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<String> optional) {",
                        "    return optional.map(v -> bar(v));",
                        "  }",
                        "  private String bar(String v) {",
                        "    return v;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<String> optional) {",
                        "    return optional.map(this::bar);",
                        "  }",
                        "  private String bar(String v) {",
                        "    return v;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAutoFix_localInstanceMethod_explicitThis() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<String> optional) {",
                        "    return optional.map(v -> this.bar(v));",
                        "  }",
                        "  private String bar(String v) {",
                        "    return v;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<String> optional) {",
                        "    return optional.map(this::bar);",
                        "  }",
                        "  private String bar(String v) {",
                        "    return v;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAutoFix_localStaticMethod() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<String> optional) {",
                        "    return optional.map(v -> bar(v));",
                        "  }",
                        "  private static String bar(String v) {",
                        "    return v;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<String> optional) {",
                        "    return optional.map(Test::bar);",
                        "  }",
                        "  private static String bar(String v) {",
                        "    return v;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAutoFix_localStaticMethod_multiParam() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Map.class.getName() + ';',
                        "class Test {",
                        "  public void foo(Map<String, String> map) {",
                        "    map.forEach((k, v) -> bar(k, v));",
                        "  }",
                        "  private static void bar(String key, String value) {",
                        "    System.out.println(key + value);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Map.class.getName() + ';',
                        "class Test {",
                        "  public void foo(Map<String, String> map) {",
                        "    map.forEach(Test::bar);",
                        "  }",
                        "  private static void bar(String key, String value) {",
                        "    System.out.println(key + value);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAutoFix_StaticMethod_multiParam() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + Map.class.getName() + ';',
                        "import " + ImmutableList.class.getName() + ';',
                        "class Test {",
                        "  public void foo(Map<String, String> map) {",
                        "    map.forEach((k, v) -> ImmutableList.of(k, v));",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Map.class.getName() + ';',
                        "import " + ImmutableList.class.getName() + ';',
                        "class Test {",
                        "  public void foo(Map<String, String> map) {",
                        "    map.forEach(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_block_localMethod() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> { return bar(); });",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(this::bar);",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testNegative_block() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> { return ImmutableList.of(1); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testPositive_expression() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    // BUG: Diagnostic contains: Lambda should be a method reference",
                        "    return optional.orElseGet(() -> ImmutableList.of());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_expression() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> ImmutableList.of());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(ImmutableList::of);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testNegative_expression_staticMethod() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Optional<String> foo(Optional<String> optional) {",
                        "    return optional.flatMap(value -> Optional.empty());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testAutoFix_expression_localMethod() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> bar());",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(this::bar);",
                        "  }",
                        "  private List<Object> bar() {",
                        "    return ImmutableList.of();",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testAutoFix_expression_referenceMethod() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "import " + Supplier.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> a, Supplier<List<Object>> b) {",
                        "    return a.orElseGet(() -> b.get());",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "import " + Supplier.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> a, Supplier<List<Object>> b) {",
                        "    return a.orElseGet(b::get);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testNegative_LocalStaticMethod_multiParam() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + Map.class.getName() + ';',
                        "class Test {",
                        "  public void foo(Map<String, Integer> map) {",
                        "    map.forEach((k, v) -> bar(v, k));",
                        "  }",
                        "  private static void bar(Integer value, String key) {",
                        "    System.out.println(key + value);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNegative_expression() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + List.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public List<Object> foo(Optional<List<Object>> optional) {",
                        "    return optional.orElseGet(() -> ImmutableList.of(1));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNegative_expression_chain() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + ImmutableList.class.getName() + ';',
                        "import " + Optional.class.getName() + ';',
                        "class Test {",
                        "  public Object foo(Optional<Object> optional) {",
                        // It's important that this is not rewritten to 'optional.orElseGet(ImmutableList.of(1)::size)'
                        // which create a new list eagerly, and returns a supplier for the new instances 'size()'
                        // function.
                        "    return optional.orElseGet(() -> ImmutableList.of(1).size());",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNegative_dont_eagerly_capture_reference() {
        compile()
                .addSourceLines(
                        "Test.java",
                        "import " + Supplier.class.getName() + ';',
                        "class Test {",
                        "  private Object mutable = null;",
                        "  public Supplier<String> foo() {",
                        "    mutable = Long.toString(System.nanoTime());",
                        // mutable::toString would not take later modifications into account
                        "    return () -> mutable.toString();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testGuavaToJavaUtilOptional() {
        refactor()
                .addInputLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "  Stream<java.util.Optional<String>> f(Stream<com.google.common.base.Optional<String>> in) {",
                        "    return in.map(value -> value.toJavaUtil());",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }
}
