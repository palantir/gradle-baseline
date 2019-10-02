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

public class AssertjMapContainsKeyTest {

    @Test
    public void simple() {
        RefasterTestHelper
                .forRefactoring(AssertjMapContainsKey.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in.keySet().contains(\"foo\")).isTrue();",
                        "    assertThat(in.containsKey(\"foo\")).isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).containsKey(\"foo\");",
                        "    assertThat(in).containsKey(\"foo\");",
                        "  }",
                        "}");
    }

    @Test
    public void description() {
        RefasterTestHelper
                .forRefactoring(AssertjMapContainsKeyWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in.keySet().contains(\"foo\")).describedAs(\"desc\").isTrue();",
                        "    assertThat(in.containsKey(\"foo\")).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, String> in) {",
                        "    assertThat(in).describedAs(\"desc\").containsKey(\"foo\");",
                        "    assertThat(in).describedAs(\"desc\").containsKey(\"foo\");",
                        "  }",
                        "}");
    }
}
