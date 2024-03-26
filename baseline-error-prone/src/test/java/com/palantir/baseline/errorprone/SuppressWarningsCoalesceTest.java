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

import org.junit.jupiter.api.Test;

class SuppressWarningsCoalesceTest {
    @Test
    void test() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  @SuppressWarnings(\"A\")",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"B\")",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"C\")",
                        "  void f() {",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  @SuppressWarnings({\"A\", \"B\", \"C\"})",
                        "  void f() {",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(SuppressWarningsCoalesce.class, getClass());
    }
}
