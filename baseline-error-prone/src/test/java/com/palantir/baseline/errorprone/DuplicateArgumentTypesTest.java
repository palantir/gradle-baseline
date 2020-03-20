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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

public final class DuplicateArgumentTypesTest {

    @Test
    void testParameterizedTypes() {
        fix().addSourceLines(
                        "Test.java",
                        "import java.util.function.Supplier;",
                        "public class Test {",
                        "  public void myah3(Supplier<Number> a, Supplier<String> b) {}",
                        "}")
                .doTest();
    }

    @Test
    void testSameType() {
        fix().addSourceLines(
                        "Test.java", "public class Test {", "  public void badMethod(Integer a, Integer b) {}", "}")
                .doTest();
    }

    @Test
    void testInheritedType() {
        fix().addSourceLines("Test.java", "public class Test {", "  public void badMethod(Integer a, Number b) {}", "}")
                .doTest();
    }

    @Test
    void testInheritedTypeOtherOrdering() {
        fix().addSourceLines("Test.java", "public class Test {", "  public void badMethod(Number a, Integer b) {}", "}")
                .doTest();
    }

    @Test
    void testMultipleArguments() {
        fix().addSourceLines(
                        "Test.java",
                        "public class Test {",
                        "  public void badMethod(Number a, String b, Double c) {}",
                        "}")
                .doTest();
    }

    @Test
    void testNoProblems() {
        fix().addSourceLines("Test.java", "public class Test {", "  public void badMethod(Number a, String b) {}", "}")
                .doTest();
    }

    @Test
    void testPrimitives() {
        fix().addSourceLines("Test.java", "public class Test {", "  public void badMethod(byte a, Number b) {}", "}")
                .doTest();
    }

    private CompilationTestHelper fix() {
        return CompilationTestHelper.newInstance(DuplicateArgumentTypes.class, getClass());
    }
}
