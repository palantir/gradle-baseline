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


public class AssertjBooleanNegationTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(
                        AssertjBooleanNegationIsFalse.class,
                        AssertjBooleanNegationIsFalseWithDescription.class,
                        AssertjBooleanNegationIsTrue.class,
                        AssertjBooleanNegationIsTrueWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(Object a, Object b, boolean bool) {",
                        "    assertThat(!bool).isTrue();",
                        "    assertThat(!a.equals(b)).isTrue();",
                        "    assertThat(!bool).isFalse();",
                        "    assertThat(!a.equals(b)).isFalse();",
                        "    assertThat(!bool).describedAs(\"desc\").isTrue();",
                        "    assertThat(!a.equals(b)).describedAs(\"desc\").isTrue();",
                        "    assertThat(!bool).describedAs(\"desc\").isFalse();",
                        "    assertThat(!a.equals(b)).describedAs(\"desc\").isFalse();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "public class Test {",
                        "  void f(Object a, Object b, boolean bool) {",
                        "    assertThat(bool).isFalse();",
                        "    assertThat(a.equals(b)).isFalse();",
                        "    assertThat(bool).isTrue();",
                        "    assertThat(a.equals(b)).isTrue();",
                        "    assertThat(bool).describedAs(\"desc\").isFalse();",
                        "    assertThat(a.equals(b)).describedAs(\"desc\").isFalse();",
                        "    assertThat(bool).describedAs(\"desc\").isTrue();",
                        "    assertThat(a.equals(b)).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}");
    }
}
