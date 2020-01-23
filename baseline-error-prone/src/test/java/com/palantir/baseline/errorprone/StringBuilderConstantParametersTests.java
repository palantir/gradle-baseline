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

package com.palantir.baseline.errorprone;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class StringBuilderConstantParametersTests {
    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(StringBuilderConstantParameters.class, getClass());
    }

    @Test
    public void shouldWarnOnConstantNumberOfParams() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       // BUG: Diagnostic contains: StringBuilder with a constant number of parameters",
                        "       return new StringBuilder().append(\"foo\").append(1).toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldWarnOnConstantNumberOfParams_fix() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       return new StringBuilder().append(\"foo\").append(1).toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java", "class Test {", "   String f() {", "       return \"foo\" + 1;", "   }", "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void shouldWarnOnConstantNumberOfParams_stringCtor() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       // BUG: Diagnostic contains: StringBuilder with a constant number of parameters",
                        "       return new StringBuilder(\"ctor\").append(\"foo\").append(1).toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldWarnOnConstantNumberOfParams_stringCtor_fix() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       return new StringBuilder(\"ctor\").append(\"foo\").append(1).toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       return \"ctor\" + \"foo\" + 1;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void shouldWarnOnConstantNumberOfParams_charSequenceCtor() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(CharSequence charSequence) {",
                        "       // BUG: Diagnostic contains: StringBuilder with a constant number of parameters",
                        "       return new StringBuilder(charSequence).append(\"foo\").append(1).toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldWarnOnConstantNumberOfParams_charSequenceCtor_fix() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(CharSequence charSequence) {",
                        "       return new StringBuilder(charSequence).append(\"foo\").append(1).toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(CharSequence charSequence) {",
                        "       return \"\" + charSequence + \"foo\" + 1;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void shouldWarnOnConstantNumberOfNonConstantParams() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(long param0, double param1) {",
                        "       // BUG: Diagnostic contains: StringBuilder with a constant number of parameters",
                        "       return new StringBuilder().append(param0).append(param1).toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldWarnOnConstantNumberOfNonConstantParams_fix() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(long param0, double param1) {",
                        "       return new StringBuilder().append(param0).append(param1).toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(long param0, double param1) {",
                        "       return \"\" + param0 + param1;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void shouldWarnOnConstantNumberOfNonConstantParams_firstString_fix() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(String param0, double param1) {",
                        "       return new StringBuilder().append(param0).append(param1).toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(String param0, double param1) {",
                        "       return param0 + param1;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void shouldWarnOnNoParams() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       // BUG: Diagnostic contains: StringBuilder with a constant number of parameters",
                        "       return new StringBuilder().toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void shouldWarnWhenCommentsArePresent() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       return new StringBuilder()",
                        "           .append(\"foo\") // comment",
                        "           .append(\"bar\")",
                        "           // BUG: Diagnostic contains: StringBuilder with a constant number of parameters",
                        "           .toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void doesNotRemoveComments() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        // Fails validation, but the tool prefers not to remove existing comments
                        "       return new StringBuilder()",
                        "           .append(\"foo\") // comment",
                        "           .append(\"bar\")",
                        "           .toString();",
                        "   }",
                        "}")
                .expectUnchanged()
                .doTestExpectingFailure(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void shouldWarnOnNoParams_fix() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       return new StringBuilder().toString();",
                        "   }",
                        "}")
                .addOutputLines("Test.java", "class Test {", "   String f() {", "       return \"\";", "   }", "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void suggestedFixRetainsCast() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object obj) {",
                        "       return new StringBuilder().append((String) obj).append(1).toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object obj) {",
                        "       return (String) obj + 1;",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void suggestedFixHandlesTernary() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object obj) {",
                        "       return new StringBuilder()",
                        "           .append(\"a\")",
                        "           .append(obj == null ? \"nil\" : obj)",
                        "           .append(\"b\")",
                        "           .toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(Object obj) {",
                        "       return \"a\" + (obj == null ? \"nil\" : obj) + \"b\";",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void suggestedFixHandlesAddition() {
        RefactoringValidator.of(new StringBuilderConstantParameters(), getClass())
                .addInputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(int param0, int param1) {",
                        "       return new StringBuilder()",
                        "           .append(\"a\")",
                        "           .append(param0 + param1)",
                        "           .append(\"b\")",
                        "           .toString();",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "class Test {",
                        "   String f(int param0, int param1) {",
                        "       return \"a\" + (param0 + param1) + \"b\";",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void negativeDynamicStringBuilder() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(int count) {",
                        "       StringBuilder sb = new StringBuilder();",
                        "       for (int i = 0; i < count; i++) {",
                        "           sb.append(i);",
                        "       }",
                        "       return sb.toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void negativeDynamicStringBuilderWithConstantAppends() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(int count) {",
                        "       StringBuilder sb = new StringBuilder();",
                        "       for (int i = 0; i < count; i++) {",
                        "           sb.append(i);",
                        "       }",
                        "       return sb.append(count).append(\"foo\").toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void negativePreSizedBuilder() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f(int count) {",
                        "       return new StringBuilder(3).append(count).toString();",
                        "   }",
                        "}")
                .doTest();
    }
}
