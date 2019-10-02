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

public class AssertjMapContainsEntryTest {

    @Test
    public void simple() {
        RefasterTestHelper
                .forRefactoring(AssertjMapContainsEntry.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, Object> in, String key, Object expected) {",
                        "    assertThat(in.get(key)).isEqualTo(expected);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, Object> in, String key, Object expected) {",
                        "    assertThat(in).containsEntry(key, expected);",
                        "  }",
                        "}");
    }

    @Test
    public void description() {
        RefasterTestHelper
                .forRefactoring(AssertjMapContainsEntryWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, Object> in, String key, Object expected) {",
                        "    assertThat(in.get(key)).describedAs(\"desc\").isEqualTo(expected);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Map;",
                        "public class Test {",
                        "  void f(Map<String, Object> in, String key, Object expected) {",
                        "    assertThat(in).describedAs(\"desc\").containsEntry(key, expected);",
                        "  }",
                        "}");
    }
}
