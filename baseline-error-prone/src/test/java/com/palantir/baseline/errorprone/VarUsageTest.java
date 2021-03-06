/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

class VarUsageTest {

    @Test
    @EnabledForJreRange(min = JRE.JAVA_10)
    void testSimple() {
        fix().addInputLines(
                        "Test.java",
                        // format
                        "class Test {",
                        "  void function() {",
                        "    var x = 3;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        // format
                        "class Test {",
                        "  void function() {",
                        "    int x = 3;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_10)
    void testWithFinalModifier() {
        fix().addInputLines(
                        "Test.java",
                        // format
                        "class Test {",
                        "  void function() {",
                        "    final var x = 3;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        // format
                        "class Test {",
                        "  void function() {",
                        "    final int x = 3;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testNegative() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "  void function(Stream<String> stream) {",
                        "    stream.forEach(var -> System.out.println(var));",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(VarUsage.class, getClass());
    }
}
