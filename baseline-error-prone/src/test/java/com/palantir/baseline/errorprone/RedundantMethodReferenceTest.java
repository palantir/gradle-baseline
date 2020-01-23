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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class RedundantMethodReferenceTest {

    @Test
    void testFix() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  String func(Optional<String> in, Supplier<String> defaultValue, Collection<String> col) {",
                        "    Comparator c = Comparator.comparing(Objects::nonNull);",
                        "    in.ifPresent(col::add);",
                        "    return in.orElseGet(defaultValue::get);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  String func(Optional<String> in, Supplier<String> defaultValue, Collection<String> col) {",
                        "    Comparator c = Comparator.comparing(Objects::nonNull);",
                        "    in.ifPresent(col::add);",
                        "    return in.orElseGet(defaultValue);",
                        "  }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testFunctionalInterfaceAdditionalMethod() {
        fix().addInputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.stream.*;",
                        "import java.util.function.*;",
                        "class Test {",
                        "  void func(Stream<String> in, List<String> items) {",
                        "    Ambiguous ambiguous = new Ambiguous();",
                        "    in.forEach(ambiguous::printError);",
                        "  }",
                        "  static class Ambiguous implements Consumer<String> {",
                        "    @Override",
                        "    public void accept(String value) {",
                        "        System.out.println(value);",
                        "    }",
                        "    public void printError(String value) {",
                        "        System.err.println(value);",
                        "    }",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new RedundantMethodReference(), getClass());
    }
}
