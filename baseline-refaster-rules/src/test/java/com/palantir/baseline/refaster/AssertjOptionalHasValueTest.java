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

public final class AssertjOptionalHasValueTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(AssertjOptionalHasValue.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<T> {",
                        "  void f(Optional<T> in, T out) {",
                        "    assertThat(in.get()).isEqualTo(out);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<T> {",
                        "  void f(Optional<T> in, T out) {",
                        "    assertThat(in).hasValue(out);",
                        "  }",
                        "}");
    }

    @Test
    public void testWithDescription() {
        RefasterTestHelper
                .forRefactoring(AssertjOptionalHasValueWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<T> {",
                        "  void f(Optional<T> in, T out) {",
                        "    assertThat(in.get()).describedAs(\"desc\").isEqualTo(out);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test<T> {",
                        "  void f(Optional<T> in, T out) {",
                        "    assertThat(in).describedAs(\"desc\").hasValue(out);",
                        "  }",
                        "}");
    }
}
