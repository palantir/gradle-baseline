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

import org.junit.jupiter.api.Test;

class OptionalFlatMapOfNullableTest {

    @Test
    void testFix_expression() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "   Optional<?> f(Optional<String> in) {",
                        "       return in.flatMap(x -> Optional.ofNullable(x));",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "   Optional<?> f(Optional<String> in) {",
                        "       return in.map(x -> x);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testFix_statement() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "   Optional<?> f(Optional<String> in) {",
                        "       return in.flatMap(x -> { return Optional.ofNullable(x); });",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "   Optional<?> f(Optional<String> in) {",
                        "       return in.map(x -> { return x; });",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void testFix_statement_additionalStatements() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "   Optional<?> f(Optional<String> in) {",
                        "       return in.flatMap(x -> {",
                        "         String y = x + x;",
                        "         return Optional.ofNullable(y);",
                        "       });",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "   Optional<?> f(Optional<String> in) {",
                        "       return in.map(x -> {",
                        "         String y = x + x;",
                        "         return y;",
                        "       });",
                        "   }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(OptionalFlatMapOfNullable.class, getClass());
    }
}
