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

package com.palantir.suppressibleerrorprone;

import org.junit.jupiter.api.Test;

class SuppressWarningsCoalesceTest {
    @Test
    void no_suppress_warnings_no_repeatable() {
        fix().addInputLines("Test.java", "public class Test {", "  void f() {", "  }", "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void single_suppress_warnings_no_repeatable() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  @SuppressWarnings(\"Something\")",
                        "  void f() {",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void multiple_suppress_warnings_single_repeatable() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  @SuppressWarnings({\"A\", \"B\"})",
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

    @Test
    void single_suppress_warnings_multiple_repeatable_warnings() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"B\")",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"C\")",
                        "  @SuppressWarnings(\"A\")",
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

    @Test
    void no_suppressible_warnings_single_repeatable_warnings() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"A\")",
                        "  void f() {",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java", "public class Test {", "  @SuppressWarnings(\"A\")", "  void f() {", "  }", "}")
                .doTest();
    }

    @Test
    void multiple_suppress_warnings_multiple_repeatable_warnings() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"C\")",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"D\")",
                        "  @SuppressWarnings({\"A\", \"B\"})",
                        "  void f() {",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  @SuppressWarnings({\"A\", \"B\", \"C\", \"D\"})",
                        "  void f() {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void field() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"C\")",
                        "  @com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"D\")",
                        "  @SuppressWarnings({\"A\", \"B\"})",
                        "  String field;",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  @SuppressWarnings({\"A\", \"B\", \"C\", \"D\"})",
                        "  String field;",
                        "}")
                .doTest();
    }

    @Test
    void klass() {
        fix().addInputLines(
                        "Test.java",
                        "@com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"C\")",
                        "@com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\"D\")",
                        "@SuppressWarnings({\"A\", \"B\"})",
                        "public class Test {}")
                .addOutputLines("Test.java", "@SuppressWarnings({\"A\", \"B\", \"C\", \"D\"})", "public class Test {}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(SuppressWarningsCoalesce.class, getClass());
    }
}
