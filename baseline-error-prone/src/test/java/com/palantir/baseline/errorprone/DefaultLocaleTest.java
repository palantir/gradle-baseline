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

class DefaultLocaleTest {

    @Test
    void testFixToLowerCase() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  String f(String s) {",
                        "    return s.toLowerCase();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Locale;",
                        "class Test {",
                        "  String f(String s) {",
                        "    return s.toLowerCase(Locale.ROOT);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testFixToUpperCase() {
        fix().addInputLines(
                        "Test.java",
                        "class Test {",
                        "  String f(String s) {",
                        "    return s.toUpperCase();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.Locale;",
                        "class Test {",
                        "  String f(String s) {",
                        "    return s.toUpperCase(Locale.ROOT);",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(DefaultLocale.class, getClass());
    }
}
