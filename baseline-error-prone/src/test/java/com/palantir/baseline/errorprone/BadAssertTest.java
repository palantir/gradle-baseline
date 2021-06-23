/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

final class BadAssertTest {

    @Test
    void testNoDescription() {
        fix().addInputLines(
                        "Test.java",
                        // format-hint
                        "public class Test {",
                        "  void f(boolean in) {",
                        "    assert in;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f(boolean in) {",
                        "    if (!in) {",
                        "        throw new IllegalStateException();",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testConstantStringDescription() {
        fix().addInputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f(boolean in) {",
                        "    assert in : \"oops\";",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.Preconditions;",
                        "public class Test {",
                        "  void f(boolean in) {",
                        "    Preconditions.checkState(in, \"oops\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testConstantNonStringDescription() {
        fix().addInputLines(
                        "Test.java",
                        // format-hint
                        "public class Test {",
                        "  void f(boolean in) {",
                        "    assert in : 1;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f(boolean in) {",
                        "    if (!in) {",
                        "        throw new IllegalStateException(String.valueOf(1));",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testNonConstantStringDescription() {
        fix().addInputLines(
                        "Test.java",
                        // format-hint
                        "public class Test {",
                        "  void f(boolean in, String desc) {",
                        "    assert in : desc;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "public class Test {",
                        "  void f(boolean in, String desc) {",
                        "    if (!in) {",
                        "        throw new IllegalStateException(desc);",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(BadAssert.class, getClass());
    }
}
