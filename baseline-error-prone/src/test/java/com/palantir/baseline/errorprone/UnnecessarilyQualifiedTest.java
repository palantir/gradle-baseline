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

class UnnecessarilyQualifiedTest {

    @Test
    void testUnnecessarilyQualified() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.Set;",
                        "class Test {",
                        "  java.util.List<java.util.Set<Object>> get() {",
                        "    return null;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.List;",
                        "import java.util.Set;",
                        "class Test {",
                        "  List<Set<Object>> get() {",
                        "    return null;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testUnnecessarilyQualifiedType() {
        fix().addInputLines("Test.java", "import java.util.List;", "class Test {", "  java.util.List value;", "}")
                .addOutputLines("Test.java", "import java.util.List;", "class Test {", "  List value;", "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(UnnecessarilyQualified.class, getClass());
    }
}
