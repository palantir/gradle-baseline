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

import org.junit.Test;

public class AssertjPrimitiveEqualsTest {
    @Test
    public void bytes() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(byte a, byte b) { assertThat(a == b).isTrue(); }",
                        "  void g(byte a, byte b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(byte a, byte b) { assertThat(a).isEqualTo(b); }",
                        "  void g(byte a, byte b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }

    @Test
    public void shorts() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(short a, short b) { assertThat(a == b).isTrue(); }",
                        "  void g(short a, short b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(short a, short b) { assertThat(a).isEqualTo(b); }",
                        "  void g(short a, short b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }

    @Test
    public void ints() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(int a, int b) { assertThat(a == b).isTrue(); }",
                        "  void g(int a, int b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(int a, int b) { assertThat(a).isEqualTo(b); }",
                        "  void g(int a, int b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }

    @Test
    public void longs() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(long a, long b) { assertThat(a == b).isTrue(); }",
                        "  void g(long a, long b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(long a, long b) { assertThat(a).isEqualTo(b); }",
                        "  void g(long a, long b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }

    @Test
    public void floats() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(float a, float b) { assertThat(a == b).isTrue(); }",
                        "  void g(float a, float b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(float a, float b) { assertThat(a).isEqualTo(b); }",
                        "  void g(float a, float b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }

    @Test
    public void doubles() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(double a, double b) { assertThat(a == b).isTrue(); }",
                        "  void g(double a, double b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(double a, double b) { assertThat(a).isEqualTo(b); }",
                        "  void g(double a, double b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }

    @Test
    public void chars() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(char a, char b) { assertThat(a == b).isTrue(); }",
                        "  void g(char a, char b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(char a, char b) { assertThat(a).isEqualTo(b); }",
                        "  void g(char a, char b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }

    @Test
    public void booleans() {
        RefasterTestHelper
                .forRefactoring(AssertjPrimitiveEquals.class, AssertjPrimitiveEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(boolean a, boolean b) { assertThat(a == b).isTrue(); }",
                        "  void g(boolean a, boolean b) { assertThat(a == b).describedAs(\"desc\").isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(boolean a, boolean b) { assertThat(a).isEqualTo(b); }",
                        "  void g(boolean a, boolean b) { assertThat(a).describedAs(\"desc\").isEqualTo(b); }",
                        "}");
    }
}
