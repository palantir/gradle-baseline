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

public class AssertjMapHasSizeTest {

    @Test
    public void exactSize_simple() {
        RefasterTestHelper.forRefactoring(AssertjMapHasSizeExactly.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in.size() == 2).isTrue();",
                        "    assertThat(in.size()).isEqualTo(2);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).hasSize(2);",
                        "    assertThat(in).hasSize(2);",
                        "  }",
                        "}");
    }

    @Test
    public void exactSize_description() {
        RefasterTestHelper.forRefactoring(AssertjMapHasSizeExactlyWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import com.google.common.collect.ImmutableList;",
                        "import java.util.Collections;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in.size() == 2).describedAs(\"desc\").isTrue();",
                        "    assertThat(in.size()).describedAs(\"desc\").isEqualTo(2);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import com.google.common.collect.ImmutableList;",
                        "import java.util.Collections;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).describedAs(\"desc\").hasSize(2);",
                        "    assertThat(in).describedAs(\"desc\").hasSize(2);",
                        "  }",
                        "}");
    }
}
