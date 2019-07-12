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

    private void testPositive(String expr) {
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

    private void testNegative(String expr) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.collect.*;",
                        "import java.util.Collections;",
                        "import java.util.Optional;",
                        "class Test {",
                        "  String f() { return \"hello\"; }",
                        "  void test() {",
                        "    Optional.empty().orElse(" + expr + ");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNonCompileTimeConstantExpression() {
        testPositive("f()");
        testPositive("s + s");
        testPositive("\"world\" + s");
        testPositive("\"world\".substring(1)");
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
    public void testCompileTimeConstantExpression() {
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

    @Test
    public void testConstantEmptyCollection() {
        testNegative("Collections.emptyEnumeration()");
        testNegative("Collections.emptyIterator()");
        testNegative("Collections.emptyList()");
        testNegative("Collections.emptyListIterator()");
        testNegative("Collections.emptyMap()");
        testNegative("Collections.emptyNavigableMap()");
        testNegative("Collections.emptyNavigableSet()");
        testNegative("Collections.emptySet()");
        testNegative("Collections.emptySortedMap()");
        testNegative("Collections.emptySortedSet()");
        testNegative("ImmutableSet.of()");
        testNegative("ImmutableBiMap.of()");
        testNegative("ImmutableClassToInstanceMap.of()");
        testNegative("ImmutableList.of()");
        testNegative("ImmutableListMultimap.of()");
        testNegative("ImmutableMap.of()");
        testNegative("ImmutableMultimap.of()");
        testNegative("ImmutableMultiset.of()");
        testNegative("ImmutableRangeMap.of()");
        testNegative("ImmutableRangeSet.of()");
        testNegative("ImmutableSet.of()");
        testNegative("ImmutableSetMultimap.of()");
        testNegative("ImmutableSortedMap.of()");
        testNegative("ImmutableSortedMultiset.of()");
        testNegative("ImmutableSortedSet.of()");
        testNegative("ImmutableTable.of()");
    }
}
