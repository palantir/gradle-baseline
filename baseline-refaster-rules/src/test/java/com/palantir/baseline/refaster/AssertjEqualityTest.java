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

public class AssertjEqualityTest {

    @Test
    public void equals_simple() {
        RefasterTestHelper
                .forRefactoring(AssertjEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj.equals(\"foo\")).isTrue();",
                        "    assertThat(!obj.equals(\"foo\")).isFalse();",
                        "    assertThat(Objects.equals(obj, \"foo\")).isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj).isEqualTo(\"foo\");",
                        "    assertThat(obj).isEqualTo(\"foo\");",
                        "    assertThat(obj).isEqualTo(\"foo\");",
                        "  }",
                        "}");
    }

    @Test
    public void equals_description() {
        RefasterTestHelper
                .forRefactoring(AssertjEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj.equals(\"foo\")).describedAs(\"desc\").isTrue();",
                        "    assertThat(!obj.equals(\"foo\")).describedAs(\"desc\").isFalse();",
                        "    assertThat(Objects.equals(obj, \"foo\")).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj).describedAs(\"desc\").isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc\").isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc\").isEqualTo(\"foo\");",
                        "  }",
                        "}");
    }

    @Test
    public void notEquals_simple() {
        RefasterTestHelper
                .forRefactoring(AssertjNotEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj.equals(\"foo\")).isFalse();",
                        "    assertThat(!obj.equals(\"foo\")).isTrue();",
                        "    assertThat(Objects.equals(obj, \"foo\")).isFalse();",
                        "    assertThat(!Objects.equals(obj, \"foo\")).isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj).isNotEqualTo(\"foo\");",
                        "    assertThat(obj).isNotEqualTo(\"foo\");",
                        "    assertThat(obj).isNotEqualTo(\"foo\");",
                        "    assertThat(obj).isNotEqualTo(\"foo\");",
                        "  }",
                        "}");
    }

    @Test
    public void notEquals_description() {
        RefasterTestHelper
                .forRefactoring(AssertjNotEqualsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj.equals(\"foo\")).describedAs(\"desc\").isFalse();",
                        "    assertThat(!obj.equals(\"foo\")).describedAs(\"desc\").isTrue();",
                        "    assertThat(Objects.equals(obj, \"foo\")).describedAs(\"desc\").isFalse();",
                        "    assertThat(!Objects.equals(obj, \"foo\")).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj).describedAs(\"desc\").isNotEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc\").isNotEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc\").isNotEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc\").isNotEqualTo(\"foo\");",
                        "  }",
                        "}");
    }
}
