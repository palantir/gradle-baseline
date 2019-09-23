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

public class AssertjIsNotZeroTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(AssertjIsNotZero.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in, int i, double d, float f, long l) {",
                        "    assertThat(in.size()).isNotEqualTo(0);",
                        "    assertThat(i).isNotEqualTo(0);",
                        "    assertThat(d).isNotEqualTo(0);",
                        "    assertThat(d).isNotEqualTo(0D);",
                        "    assertThat(d).isNotEqualTo(0.0D);",
                        "    assertThat(f).isNotEqualTo(0);",
                        "    assertThat(f).isNotEqualTo(0.0);",
                        "    assertThat(l).isNotEqualTo(0);",
                        "    assertThat(l).isNotEqualTo(0L);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.List;",
                        "public class Test {",
                        "  void f(List<String> in, int i, double d, float f, long l) {",
                        "    assertThat(in.size()).isNotZero();",
                        "    assertThat(i).isNotZero();",
                        "    assertThat(d).isNotZero();",
                        "    assertThat(d).isNotZero();",
                        "    assertThat(d).isNotZero();",
                        "    assertThat(f).isNotZero();",
                        "    assertThat(f).isNotZero();",
                        "    assertThat(l).isNotZero();",
                        "    assertThat(l).isNotZero();",
                        "  }",
                        "}");
    }

}
