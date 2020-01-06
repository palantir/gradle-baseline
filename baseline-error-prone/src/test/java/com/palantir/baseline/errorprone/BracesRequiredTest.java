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
import org.junit.jupiter.api.Test;

public class BracesRequiredTest {

    @Test
    void testFix_if_then() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param) System.out.println();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param) {",
                        "        System.out.println();",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_if_else() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param) {",
                        "      System.out.println(\"if\");",
                        "    } else",
                        "      System.out.println(\"else\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param) {",
                        "      System.out.println(\"if\");",
                        "    } else {",
                        "      System.out.println(\"else\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_if_both() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param)",
                        "      System.out.println(\"if\");",
                        "    else",
                        "      System.out.println(\"else\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param) {",
                        "      System.out.println(\"if\");",
                        "    } else {",
                        "      System.out.println(\"else\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_if_emptyThen() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param); else",
                        "      System.out.println(\"else\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param); else {",
                        "      System.out.println(\"else\");",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_while() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    while (param) System.out.println();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    while (param) {",
                        "        System.out.println();",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_doWhile() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    do System.out.println(); while (param);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    do {",
                        "        System.out.println();",
                        "    } while (param);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_for() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    for (int i = 0; i < 5; i++) System.out.println();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    for (int i = 0; i < 5; i++) {",
                        "        System.out.println();",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_enhancedFor() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "  void f(List<String> list) {",
                        "    for (String item : list) System.out.println(item);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "  void f(List<String> list) {",
                        "    for (String item : list) {",
                        "      System.out.println(item);",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_nested() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param) if (param) {",
                        "      System.out.println();",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean param) {",
                        "    if (param) {",
                        "        if (param) {",
                        "            System.out.println();",
                        "        }",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFix_elseIf() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean p0, boolean p1) {",
                        "    if (p0) System.out.println();",
                        "    else if (p1) System.out.println();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(boolean p0, boolean p1) {",
                        "    if (p0) {",
                        "      System.out.println();",
                        "    } else if (p1) {",
                        "      System.out.println();",
                        "    }",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new BracesRequired(), getClass());
    }
}
