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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class LambdaMethodReferenceTest {

    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringValidator;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(LambdaMethodReference.class, getClass());
        refactoringValidator = RefactoringValidator.of(new LambdaMethodReference(), getClass());
    }

    @Test
    public void testMethodReference() {
        compilationHelper
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
        compilationHelper
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
        compilationHelper
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
        compilationHelper
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
        compilationHelper
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
        compilationHelper
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
        compilationHelper
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
        refactoringValidator
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
    void testAutoFix_InstanceMethod() {
        refactoringValidator
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
    void testAutoFix_SpecificInstanceMethod() {
        refactoringValidator
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
        refactoringValidator
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
        refactoringValidator
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
        refactoringValidator
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
        refactoringValidator
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
        refactoringValidator
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
        refactoringValidator
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
        refactoringValidator
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
        compilationHelper
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
        compilationHelper
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
        refactoringValidator
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
        compilationHelper
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
        refactoringValidator
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
        refactoringValidator
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
        compilationHelper
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
        compilationHelper
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
        compilationHelper
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
}
