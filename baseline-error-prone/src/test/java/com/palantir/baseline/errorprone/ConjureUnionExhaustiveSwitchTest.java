/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

public class ConjureUnionExhaustiveSwitchTest {

    @Test
    void testConjureUnionSwitchWithDefault() {
        String source = """
            sealed abstract class Shape permits Circle, Square, Unknown {
                sealed interface Known permits Circle, Square {}
            }
            final class Circle extends Shape implements Shape.Known {}
            final class Square extends Shape implements Shape.Known {}
            final class Unknown extends Shape {}
            public class Test {
                public void test(Shape shape) {
                    // BUG: Diagnostic contains: Avoid using default clause
                    switch (shape) {
                        case Circle circle -> System.out.println("Circle");
                        default -> System.out.println("Unknown");
                    }
                }
            }
            """;
        helper().addSourceLines("Test.java", source).doTest();
    }

    @Test
    void testConjureUnionSwitchWithoutDefault() {
        String source = """
            sealed abstract class Shape permits Circle, Square, Unknown {
                sealed interface Known permits Circle, Square {}
            }
            final class Circle extends Shape implements Shape.Known {}
            final class Square extends Shape implements Shape.Known {}
            final class Unknown extends Shape {}
            public class Test {
                public void test(Shape shape) {
                    switch (shape) {
                        case Circle circle -> System.out.println("Circle");
                        case Square square -> System.out.println("Square");
                        case Unknown unknown -> System.out.println("Unknown");
                    }
                }
            }
            """;
        helper().addSourceLines("Test.java", source).doTest();
    }

    @Test
    void testNonConjureUnionSealedClassWithDefault() {
        String source = """
            sealed abstract class Shape permits Circle, Square {}
            final class Circle extends Shape {}
            final class Square extends Shape {}
            public class Test {
                public void test(Shape shape) {
                    switch (shape) {
                        case Circle circle -> System.out.println("Circle");
                        default -> System.out.println("Unknown");
                    }
                }
            }
            """;
        helper().addSourceLines("Test.java", source).doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ConjureUnionExhaustiveSwitch.class, getClass());
    }
}
