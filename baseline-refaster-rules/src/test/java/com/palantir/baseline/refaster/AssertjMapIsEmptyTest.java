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

public class AssertjMapIsEmptyTest {

    @Test
    public void simple() {
        RefasterTestHelper.forRefactoring(AssertjMapIsEmpty.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in.size() == 0).isTrue();",
                        "    assertThat(in.isEmpty()).isTrue();",
                        "    assertThat(in.size()).isEqualTo(0);",
                        "    assertThat(in.size()).isZero();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).isEmpty();",
                        "    assertThat(in).isEmpty();",
                        "    assertThat(in).isEmpty();",
                        "    assertThat(in).isEmpty();",
                        "  }",
                        "}");
    }

    @Test
    public void description() {
        RefasterTestHelper.forRefactoring(AssertjMapIsEmptyWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in.size() == 0).describedAs(\"desc\").isTrue();",
                        "    assertThat(in.isEmpty()).describedAs(\"desc\").isTrue();",
                        "    assertThat(in.size()).describedAs(\"desc\").isEqualTo(0);",
                        "    assertThat(in.size()).describedAs(\"desc\").isZero();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).describedAs(\"desc\").isEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isEmpty();",
                        "  }",
                        "}");
    }

    @Test
    public void test2() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(AssertjMapIsEmpty2.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import com.google.common.collect.ImmutableMap;",
                        "import java.util.Collections;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).hasSize(0);",
                        "    assertThat(in).isEqualTo(ImmutableMap.of());",
                        "    assertThat(in).isEqualTo(Collections.emptyMap());",
                        "    assertThat(in).describedAs(\"desc\").hasSize(0);",
                        "    assertThat(in).describedAs(\"desc\").isEqualTo(ImmutableMap.of());",
                        "    assertThat(in).describedAs(\"desc\").isEqualTo(Collections.emptyMap());",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import com.google.common.collect.ImmutableMap;",
                        "import java.util.Collections;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).isEmpty();",
                        "    assertThat(in).isEmpty();",
                        "    assertThat(in).isEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isEmpty();",
                        "  }",
                        "}");
    }
}
