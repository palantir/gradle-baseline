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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class UnnecessaryLambdaArgumentParenthesesTest {

    @Test
    public void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import " + Predicate.class.getName() + ';',
                        "class Test {",
                        "    Predicate<Object> a = (value) -> value == null;",
                        "    Predicate<Object> b = ( value ) -> value == null;",
                        "    Predicate<Object> c = /* (value) -> value*/(value) -> value == null;",
                        "    Predicate<Object> d = (value) /*(value) -> value*/ -> value == null;",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Predicate.class.getName() + ';',
                        "class Test {",
                        "    Predicate<Object> a = value -> value == null;",
                        "    Predicate<Object> b =  value  -> value == null;",
                        "    Predicate<Object> c = /* (value) -> value*/value -> value == null;",
                        "    Predicate<Object> d = value /*(value) -> value*/ -> value == null;",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void testNegative() {
        fix().addInputLines(
                        "Test.java",
                        "import " + Predicate.class.getName() + ';',
                        "import " + LongPredicate.class.getName() + ';',
                        "class Test {",
                        "    Predicate<Object> a = value -> value == null;",
                        "    Predicate<Object> b =  value  -> value == null;",
                        "    Predicate<Object> c = /* (value) -> value*/value -> value == null;",
                        "    Predicate<Object> d = value /*(value) -> value*/ -> value == null;",
                        "    Predicate<?> e = (String value) -> value == null;",
                        "    LongPredicate f = (LongPredicate) (long value) -> value == 0L;",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(UnnecessaryLambdaArgumentParentheses.class, getClass());
    }
}
