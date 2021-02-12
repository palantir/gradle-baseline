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

class SafeLoggingPreconditionsMigrationTest {

    @Test
    void testFullyQualified() {
        helper().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(Object o) {",
                        "    com.palantir.logsafe.Preconditions.checkNotNull(o);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "  void f(Object o) {",
                        "    com.palantir.logsafe.preconditions.Preconditions.checkNotNull(o);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testClassImport() {
        helper().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.Preconditions;",
                        "class Test {",
                        "  void f(Object o) {",
                        "    Preconditions.checkNotNull(o);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.preconditions.Preconditions;",
                        "class Test {",
                        "  void f(Object o) {",
                        "    Preconditions.checkNotNull(o);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticImport() {
        helper().addInputLines(
                        "Test.java",
                        "import static com.palantir.logsafe.Preconditions.checkNotNull;",
                        "class Test {",
                        "  void f(Object o) {",
                        "    checkNotNull(o);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static com.palantir.logsafe.preconditions.Preconditions.checkNotNull;",
                        "class Test {",
                        "  void f(Object o) {",
                        "    checkNotNull(o);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testStaticStarImport() {
        helper().addInputLines(
                        "Test.java",
                        "import static com.palantir.logsafe.Preconditions.*;",
                        "class Test {",
                        "  void f(Object o) {",
                        "    checkNotNull(o);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import static com.palantir.logsafe.preconditions.Preconditions.*;",
                        "class Test {",
                        "  void f(Object o) {",
                        "    checkNotNull(o);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator helper() {
        return RefactoringValidator.of(new SafeLoggingPreconditionsMigration(), getClass());
    }
}
