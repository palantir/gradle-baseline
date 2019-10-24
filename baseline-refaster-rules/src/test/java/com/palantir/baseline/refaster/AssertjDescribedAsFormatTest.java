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

public class AssertjDescribedAsFormatTest {

    @Test
    public void test() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not currently support fluent refactors on java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(AssertjDescribedAsFormat.class)
                .withInputLines(
                        "Test",
                        "import static java.lang.String.format;",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj).isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc\").isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc %s\", \"arg\").isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(String.format(\"desc %s\", \"arg\")).isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(format(\"desc %s\", \"arg\")).isEqualTo(\"foo\");",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static java.lang.String.format;",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(Object obj) {",
                        "    assertThat(obj).isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc\").isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc %s\", \"arg\").isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc %s\", \"arg\").isEqualTo(\"foo\");",
                        "    assertThat(obj).describedAs(\"desc %s\", \"arg\").isEqualTo(\"foo\");",
                        "  }",
                        "}");
    }
}
