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

public class AssertjStringContentTest {

    @Test
    public void contains_simple() {
        RefasterTestHelper
                .forRefactoring(AssertjStringContains.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str.contains(\"foo\")).isTrue();",
                        "    assertThat(!str.contains(\"foo\")).isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str).contains(\"foo\");",
                        "    assertThat(str).contains(\"foo\");",
                        "  }",
                        "}");
    }

    @Test
    public void contains_description() {
        RefasterTestHelper
                .forRefactoring(AssertjStringContainsWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str.contains(\"foo\")).describedAs(\"desc\").isTrue();",
                        "    assertThat(!str.contains(\"foo\")).describedAs(\"desc\").isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str).describedAs(\"desc\").contains(\"foo\");",
                        "    assertThat(str).describedAs(\"desc\").contains(\"foo\");",
                        "  }",
                        "}");
    }

    @Test
    public void notContain_simple() {
        RefasterTestHelper
                .forRefactoring(AssertjStringDoesNotContain.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str.contains(\"foo\")).isFalse();",
                        "    assertThat(!str.contains(\"foo\")).isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str).doesNotContain(\"foo\");",
                        "    assertThat(str).doesNotContain(\"foo\");",
                        "  }",
                        "}");
    }

    @Test
    public void notContain_description() {
        RefasterTestHelper
                .forRefactoring(AssertjStringDoesNotContainWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str.contains(\"foo\")).describedAs(\"desc\").isFalse();",
                        "    assertThat(!str.contains(\"foo\")).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(String str) {",
                        "    assertThat(str).describedAs(\"desc\").doesNotContain(\"foo\");",
                        "    assertThat(str).describedAs(\"desc\").doesNotContain(\"foo\");",
                        "  }",
                        "}");
    }
}
