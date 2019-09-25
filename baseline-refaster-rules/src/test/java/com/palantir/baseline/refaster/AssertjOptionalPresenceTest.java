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

public class AssertjOptionalPresenceTest {

    @Test
    public void isPresent_simple() {
        RefasterTestHelper
                .forRefactoring(AssertjOptionalIsPresent.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test {",
                        "  void f(Optional<String> in) {",
                        "    assertThat(in.isPresent()).isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test {",
                        "  void f(Optional<String> in) {",
                        "    assertThat(in).isPresent();",
                        "  }",
                        "}");
    }

    @Test
    public void isPresent_description() {
        RefasterTestHelper
                .forRefactoring(AssertjOptionalIsPresentWithDescription.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test {",
                        "  void f(Optional<String> in) {",
                        "    assertThat(in.isPresent()).describedAs(\"desc\").isTrue();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Optional;",
                        "public class Test {",
                        "  void f(Optional<String> in) {",
                        "    assertThat(in).describedAs(\"desc\").isPresent();",
                        "  }",
                        "}");
    }
}
