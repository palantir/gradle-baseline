/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone;

import org.junit.jupiter.api.Test;

class DeprecatedGuavaObjectsTest {

    @Test
    public void test() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.base.Objects;",
                        "import static com.google.common.base.Objects.equal;",
                        "public class Test {",
                        "  void f(Object a, Object b) {",
                        "    com.google.common.base.Objects.equal(a, b);",
                        "    Objects.equal(a, b);",
                        "    equal(a, b);",
                        "    com.google.common.base.Objects.hashCode(a, b);",
                        "    Objects.hashCode(a, b);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Objects;",
                        "public class Test {",
                        "  void f(Object a, Object b) {",
                        "    Objects.equals(a, b);",
                        "    Objects.equals(a, b);",
                        "    Objects.equals(a, b);",
                        "    Objects.hash(a, b);",
                        "    Objects.hash(a, b);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new DeprecatedGuavaObjects(), getClass());
    }
}
