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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.bugpatterns.UnnecessaryParentheses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

public class UnnecessaryParenthesesTest {

    @Test
    @DisabledForJreRange(max = JRE.JAVA_13)
    public void testSwitchExpression() {
        CompilationTestHelper compilationHelper = CompilationTestHelper.newInstance(
                        UnnecessaryParentheses.class, getClass())
                .setArgs(ImmutableList.of("--enable-preview", "--release", "15"));

        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  static int foo(String value) {",
                        "    // BUG: Diagnostic contains: These grouping parentheses are unnecessary",
                        "    return switch (value) {",
                        "      case \"Foo\" -> 10;",
                        "      default -> 0;",
                        "    };",
                        "  }",
                        "}")
                .doTest();
    }
}
