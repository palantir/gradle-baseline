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

public class AssertjCollectionIsNotEmptyTest {

    @Test
    public void simple() {
        RefasterTestHelper.forRefactoring(AssertjCollectionIsNotEmpty.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in) {",
                        "    assertThat(in.size() != 0).isTrue();",
                        "    assertThat(in.size() == 0).isFalse();",
                        "    assertThat(in.isEmpty()).isFalse();",
                        "    assertThat(!in.isEmpty()).isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in) {",
                        "    assertThat(in).isNotEmpty();",
                        "    assertThat(in).isNotEmpty();",
                        "    assertThat(in).isNotEmpty();",
                        "    assertThat(in).isNotEmpty();",
                        "  }",
                        "}");
    }

    @Test
    public void description() {
        RefasterTestHelper.forRefactoring(AssertjCollectionIsNotEmptyWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in) {",
                        "    assertThat(in.size() != 0).describedAs(\"desc\").isTrue();",
                        "    assertThat(in.size() == 0).describedAs(\"desc\").isFalse();",
                        "    assertThat(in.isEmpty()).describedAs(\"desc\").isFalse();",
                        "    assertThat(!in.isEmpty()).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in) {",
                        "    assertThat(in).describedAs(\"desc\").isNotEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isNotEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isNotEmpty();",
                        "    assertThat(in).describedAs(\"desc\").isNotEmpty();",
                        "  }",
                        "}");
    }
}
