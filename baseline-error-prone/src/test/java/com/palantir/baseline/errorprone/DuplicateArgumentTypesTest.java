/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.Test;

public final class DuplicateArgumentTypesTest {

    private static final BugCheckerRefactoringTestHelper.TestMode TEST_MODE =
            BugCheckerRefactoringTestHelper.TestMode.AST_MATCH;

    @Test
    void testSameType() {
        validator()
                .addInputLines(
                        "Test.java", "public class Test {", "  public void badMethod(Integer a, Integer b) {}", "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testInheritedType() {
        validator()
                .addInputLines(
                        "Test.java", "public class Test {", "  public void badMethod(Integer a, Number b) {}", "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testInheritedTypeOtherOrdering() {
        validator()
                .addInputLines(
                        "Test.java", "public class Test {", "  public void badMethod(Number a, Integer b) {}", "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testMultipleArguments() {
        validator()
                .addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  public void badMethod(Number a, String b, Double c) {}",
                        "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testPrimitives() {
        validator()
                .addInputLines("Test.java", "public class Test {", "  public void badMethod(byte a, Number b) {}", "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testParameterizedTypesWithNonSubTypedParameters() {
        validator()
                .addInputLines(
                        "Test.java",
                        "import java.util.function.Supplier;",
                        "public class Test {",
                        "  public void goodMethod(Supplier<Number> a, Supplier<String> b) {}",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testParameterizedTypesWithSubTypedParameters() {
        validator()
                .addInputLines(
                        "Test.java",
                        "import java.util.function.Supplier;",
                        "public class Test {",
                        "  public void badMethod(Supplier<Number> a, Supplier<Integer> b) {}",
                        "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testParameterizedTypesWithParameterizedMethodType() {
        validator()
                .addInputLines(
                        "Test.java",
                        "import java.util.function.Supplier;",
                        "public class Test {",
                        "  public <T, U extends T> void badMethod(Supplier<U> a, Supplier<T> b) {}",
                        "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testNestedParameterizedTypes() {
        validator()
                .addInputLines(
                        "Test.java",
                        "import java.util.function.Supplier;",
                        "import java.util.function.Function;",
                        "public class Test {",
                        "  public void badMethod(Supplier<Function<Integer, Double>> a, Supplier<Function<Number,"
                                + " Number>> b) {}",
                        "}")
                .expectUnchanged()
                .doTestExpectingFailure(TEST_MODE);
    }

    @Test
    void testNestedParameterizedTypesWithNonSubTypeParameters() {
        validator()
                .addInputLines(
                        "Test.java",
                        "import java.util.function.Supplier;",
                        "import java.util.function.Function;",
                        "public class Test {",
                        "  public void goodMethod1(Supplier<Function<Integer, String>> a, Supplier<Function<String,"
                                + " Integer>> b) {}",
                        "  public void goodMethod2(Supplier<Function<Integer, String>> a, Supplier<Number> b) {}",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testMethodWithNoSubTypes() {
        validator()
                .addInputLines(
                        "Test.java", "public class Test {", "  public void goodMethod(Number a, String b) {}", "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator validator() {
        return RefactoringValidator.of(new DuplicateArgumentTypes(), getClass());
    }
}
