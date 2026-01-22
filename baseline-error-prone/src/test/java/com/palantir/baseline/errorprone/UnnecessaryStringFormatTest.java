/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class UnnecessaryStringFormatTest {

    @Test
    public void shouldWarnOnStringFormatWithOnlyStringArgs() {
        CompilationTestHelper.newInstance(UnnecessaryStringFormat.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(String foo, String bar) {",
                        "       // BUG: Diagnostic contains: UnnecessaryStringFormat",
                        "       return String.format(\"%s/%s\", foo, bar);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldWarnOnStringFormatWithSimpleNonStringArgs() {
        CompilationTestHelper.newInstance(UnnecessaryStringFormat.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(String foo, long bar) {",
                        "       // BUG: Diagnostic contains: UnnecessaryStringFormat",
                        "       return String.format(\"%s/%d\", foo, bar);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldNotWarnOnStringFormatWithComplexNonStringArgs() {
        CompilationTestHelper.newInstance(UnnecessaryStringFormat.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(String foo, double bar) {",
                        "       return String.format(\"%s/%.3d\", foo, bar);",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void suggestedFixSimple() {
        RefactoringValidator.of(UnnecessaryStringFormat.class, getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object foo, long bar) {",
                        "       return String.format(\"%s/%s\", foo, bar);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object foo, long bar) {",
                        "       return \"\" + foo + \"/\" + bar;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void suggestedFixComplex() {
        RefactoringValidator.of(UnnecessaryStringFormat.class, getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object foo, long bar, String baz) {",
                        "       return String.format(\"%s%s%%%s\", foo, bar, baz);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object foo, long bar, String baz) {",
                        "       return \"\" + foo + bar + \"%\" + baz;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }
}
