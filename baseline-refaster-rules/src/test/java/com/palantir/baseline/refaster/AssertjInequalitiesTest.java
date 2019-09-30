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

package com.palantir.baseline.refaster;

import static org.assertj.core.api.Assumptions.assumeThat;

import org.junit.Test;

public class AssertjInequalitiesTest {

    @Test
    public void less_than() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjLessThan.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a < b).isTrue();",
                        "    assertThat(a >= b).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isLessThan(b);",
                        "    assertThat(a).isLessThan(b);",
                        "  }",
                        "}");
    }

    @Test
    public void less_than_with_description() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjLessThanWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a < b).describedAs(desc).isTrue();",
                        "    assertThat(a >= b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isLessThan(b);",
                        "    assertThat(a).describedAs(desc).isLessThan(b);",
                        "  }",
                        "}");
    }

    @Test
    public void greater_than() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjGreaterThan.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a > b).isTrue();",
                        "    assertThat(a <= b).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isGreaterThan(b);",
                        "    assertThat(a).isGreaterThan(b);",
                        "  }",
                        "}");
    }

    @Test
    public void greater_than_with_description() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjGreaterThanWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a > b).describedAs(desc).isTrue();",
                        "    assertThat(a <= b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isGreaterThan(b);",
                        "    assertThat(a).describedAs(desc).isGreaterThan(b);",
                        "  }",
                        "}");
    }

    @Test
    public void less_than_or_equal_to() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjLessThanOrEqualTo.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a <= b).isTrue();",
                        "    assertThat(a > b).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isLessThanOrEqualTo(b);",
                        "    assertThat(a).isLessThanOrEqualTo(b);",
                        "  }",
                        "}");
    }

    @Test
    public void less_than_or_equal_to_with_description() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjLessThanOrEqualToWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a <= b).describedAs(desc).isTrue();",
                        "    assertThat(a > b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isLessThanOrEqualTo(b);",
                        "    assertThat(a).describedAs(desc).isLessThanOrEqualTo(b);",
                        "  }",
                        "}");
    }

    @Test
    public void greater_than_or_equal_to() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjGreaterThanOrEqualTo.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a >= b).isTrue();",
                        "    assertThat(a < b).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b) {",
                        "    assertThat(a).isGreaterThanOrEqualTo(b);",
                        "    assertThat(a).isGreaterThanOrEqualTo(b);",
                        "  }",
                        "}");
    }

    @Test
    public void greater_than_or_equal_to_with_description() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper
                .forRefactoring(AssertjGreaterThanOrEqualToWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a >= b).describedAs(desc).isTrue();",
                        "    assertThat(a < b).describedAs(desc).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void test(int a, int b, String desc) {",
                        "    assertThat(a).describedAs(desc).isGreaterThanOrEqualTo(b);",
                        "    assertThat(a).describedAs(desc).isGreaterThanOrEqualTo(b);",
                        "  }",
                        "}");
    }
}
