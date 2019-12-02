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

class AssertjPrimitiveComparisonTest {

    @Test
    void testComparisons() {
        fix()
                .addInputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "class Test {",
                        "  void f() {",
                        "    assertThat(1 == 2).isTrue();",
                        "    assertThat(1 == 2).isFalse();",
                        "    assertThat(1 != 2).as(\"desc\").isTrue();",
                        "    assertThat(1 != 2).describedAs(\"desc\").withThreadDumpOnError().isFalse();",
                        "    assertThat(1 > 2).withRepresentation(null).isTrue();",
                        "    assertThat(1 > 2).withFailMessage(\"fail\").isFalse();",
                        "    assertThat(1 >= 2).overridingErrorMessage(\"fail\").isTrue();",
                        "    assertThat(1 >= 2).isFalse();",
                        "    assertThat(1 < 2).isTrue();",
                        "    assertThat(1 < 2).isFalse();",
                        "    assertThat(1 <= 2).isTrue();",
                        "    assertThat(1 <= 2).isFalse();",
                        "    assertThat(1 > 2L).isTrue();",
                        "    assertThat(1 > 2).isFalse().isEqualTo(2 > 3);",
                        "    Long first = 1L;",
                        "    Long second = 2L;",
                        "    assertThat(first < second).isTrue();",
                        "    assertThat(first < 2L).isTrue();",
                        "    assertThat(1L < 2).isTrue();",
                        "    assertThat(1L + 2f < 2 + 3D).isTrue();",
                        "    assertThat(3D > 1).isTrue();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "class Test {",
                        "  void f() {",
                        "    assertThat(1).isEqualTo(2);",
                        "    assertThat(1).isNotEqualTo(2);",
                        "    assertThat(1).as(\"desc\").isNotEqualTo(2);",
                        "    assertThat(1).describedAs(\"desc\").withThreadDumpOnError().isEqualTo(2);",
                        "    assertThat(1).withRepresentation(null).isGreaterThan(2);",
                        "    assertThat(1).withFailMessage(\"fail\").isLessThanOrEqualTo(2);",
                        "    assertThat(1).overridingErrorMessage(\"fail\").isGreaterThanOrEqualTo(2);",
                        "    assertThat(1).isLessThan(2);",
                        "    assertThat(1).isLessThan(2);",
                        "    assertThat(1).isGreaterThanOrEqualTo(2);",
                        "    assertThat(1).isLessThanOrEqualTo(2);",
                        "    assertThat(1).isGreaterThan(2);",
                        "    assertThat((long) 1).isGreaterThan(2L);",
                        "    assertThat(1 > 2).isFalse().isEqualTo(2 > 3);",
                        "    Long first = 1L;",
                        "    Long second = 2L;",
                        "    assertThat(first).isLessThan(second);",
                        "    assertThat(first).isLessThan(2L);",
                        "    assertThat(1L).isLessThan(2);",
                        "    assertThat((double) (1L + 2f)).isLessThan(2 + 3D);",
                        "    assertThat(3D).isGreaterThan(1);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void less_than() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a < b).isTrue();",
                        "    assertThat(a >= b).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isLessThan(b);",
                        "    assertThat(a).isLessThan(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void less_than_with_description() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a < b).describedAs(desc).isTrue();",
                        "    assertThat(a >= b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isLessThan(b);",
                        "    assertThat(a).describedAs(desc).isLessThan(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void greater_than() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a > b).isTrue();",
                        "    assertThat(a <= b).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isGreaterThan(b);",
                        "    assertThat(a).isGreaterThan(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void greater_than_with_description() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a > b).describedAs(desc).isTrue();",
                        "    assertThat(a <= b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isGreaterThan(b);",
                        "    assertThat(a).describedAs(desc).isGreaterThan(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void less_than_or_equal_to() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a <= b).isTrue();",
                        "    assertThat(a > b).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isLessThanOrEqualTo(b);",
                        "    assertThat(a).isLessThanOrEqualTo(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void less_than_or_equal_to_with_description() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a <= b).describedAs(desc).isTrue();",
                        "    assertThat(a > b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isLessThanOrEqualTo(b);",
                        "    assertThat(a).describedAs(desc).isLessThanOrEqualTo(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void greater_than_or_equal_to() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a >= b).isTrue();",
                        "    assertThat(a < b).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isGreaterThanOrEqualTo(b);",
                        "    assertThat(a).isGreaterThanOrEqualTo(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void greater_than_or_equal_to_with_description() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a >= b).describedAs(desc).isTrue();",
                        "    assertThat(a < b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .addOutputLines("Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isGreaterThanOrEqualTo(b);",
                        "    assertThat(a).describedAs(desc).isGreaterThanOrEqualTo(b);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void bytes() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(byte a, byte b) { assertThat(a == b).isTrue(); }",
                        "  void g(byte a, byte b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(byte a, byte b) { assertThat(a).isEqualTo(b); }",
                        "  void g(byte a, byte b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void shorts() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(short a, short b) { assertThat(a == b).isTrue(); }",
                        "  void g(short a, short b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(short a, short b) { assertThat(a).isEqualTo(b); }",
                        "  void g(short a, short b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void ints() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(int a, int b) { assertThat(a == b).isTrue(); }",
                        "  void g(int a, int b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(int a, int b) { assertThat(a).isEqualTo(b); }",
                        "  void g(int a, int b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void longs() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(long a, long b) { assertThat(a == b).isTrue(); }",
                        "  void g(long a, long b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(long a, long b) { assertThat(a).isEqualTo(b); }",
                        "  void g(long a, long b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void floats() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(float a, float b) { assertThat(a == b).isTrue(); }",
                        "  void g(float a, float b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(float a, float b) { assertThat(a).isEqualTo(b); }",
                        "  void g(float a, float b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void doubles() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(double a, double b) { assertThat(a == b).isTrue(); }",
                        "  void g(double a, double b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(double a, double b) { assertThat(a).isEqualTo(b); }",
                        "  void g(double a, double b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void chars() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(char a, char b) { assertThat(a == b).isTrue(); }",
                        "  void g(char a, char b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(char a, char b) { assertThat(a).isEqualTo(b); }",
                        "  void g(char a, char b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void booleans() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(boolean a, boolean b) { assertThat(a == b).isTrue(); }",
                        "  void g(boolean a, boolean b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(boolean a, boolean b) { assertThat(a).isEqualTo(b); }",
                        "  void g(boolean a, boolean b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new AssertjPrimitiveComparison(), getClass());
    }
}
