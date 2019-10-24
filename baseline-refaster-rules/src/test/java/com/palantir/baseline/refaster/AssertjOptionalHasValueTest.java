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

public final class AssertjOptionalHasValueTest {

    @Test
    public void test() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(AssertjOptionalHasValue.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<String> {",
                        "  void f(Optional<String> in, String out) {",
                        "    assertThat(in.get()).isEqualTo(out);",
                        "    assertThat(in.isPresent() && in.get().equals(out)).isTrue();",
                        "  }",
                        "  void g(Optional<String> in, String out) {",
                        "    assertThat(in).isPresent();",
                        "    assertThat(in).hasValue(out);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<String> {",
                        "  void f(Optional<String> in, String out) {",
                        "    assertThat(in).hasValue(out);",
                        "    assertThat(in).hasValue(out);",
                        "  }",
                        "  void g(Optional<String> in, String out) {",
                        "    assertThat(in).hasValue(out);",
                        "    ",
                        "  }",
                        "}");
    }

    @Test
    public void testWithDescription() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(AssertjOptionalHasValueWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<String> {",
                        "  void f(Optional<String> in, String out) {",
                        "    assertThat(in.get()).describedAs(\"desc\").isEqualTo(out);",
                        "    assertThat(in.isPresent() && in.get().equals(out)).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<String> {",
                        "  void f(Optional<String> in, String out) {",
                        "    assertThat(in).describedAs(\"desc\").hasValue(out);",
                        "    assertThat(in).describedAs(\"desc\").hasValue(out);",
                        "  }",
                        "}");
    }

    @Test
    public void testWithDescriptionRedundant() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(AssertjOptionalHasValueRedundantWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<String> {",
                        "  void g(Optional<String> in, String out) {",
                        "    assertThat(in).describedAs(\"a\").isPresent();",
                        "    assertThat(in).describedAs(\"b\").hasValue(out);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<String> {",
                        "  void g(Optional<String> in, String out) {",
                        "    assertThat(in).describedAs(\"b\").hasValue(out);",
                        "    ",
                        "  }",
                        "}");
    }
}
