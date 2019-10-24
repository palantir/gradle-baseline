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

public class AssertjNullTest {

    @Test
    public void test() {
        RefasterTestHelper.forRefactoring(
                        AssertjIsNull.class,
                        AssertjIsNullWithDescription.class,
                        AssertjNotNull.class,
                        AssertjNotNullWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj == null).isTrue();",
                        "    assertThat(obj != null).isFalse();",
                        "    assertThat(Objects.isNull(obj)).isTrue();",
                        "    assertThat(Objects.nonNull(obj)).isFalse();",
                        "    assertThat(obj == null).isFalse();",
                        "    assertThat(obj != null).isTrue();",
                        "    assertThat(Objects.isNull(obj)).isFalse();",
                        "    assertThat(Objects.nonNull(obj)).isTrue();",
                        "    assertThat(obj == null).describedAs(\"desc\").isTrue();",
                        "    assertThat(obj != null).describedAs(\"desc\").isFalse();",
                        "    assertThat(Objects.isNull(obj)).describedAs(\"desc\").isTrue();",
                        "    assertThat(Objects.nonNull(obj)).describedAs(\"desc\").isFalse();",
                        "    assertThat(obj == null).describedAs(\"desc\").isFalse();",
                        "    assertThat(obj != null).describedAs(\"desc\").isTrue();",
                        "    assertThat(Objects.isNull(obj)).describedAs(\"desc\").isFalse();",
                        "    assertThat(Objects.nonNull(obj)).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj).isNull();",
                        "    assertThat(obj).isNull();",
                        "    assertThat(obj).isNull();",
                        "    assertThat(obj).isNull();",
                        "    assertThat(obj).isNotNull();",
                        "    assertThat(obj).isNotNull();",
                        "    assertThat(obj).isNotNull();",
                        "    assertThat(obj).isNotNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNotNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNotNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNotNull();",
                        "    assertThat(obj).describedAs(\"desc\").isNotNull();",
                        "  }",
                        "}");
    }
}
