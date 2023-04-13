/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import java.util.Optional;
import org.junit.jupiter.api.Test;

public class OptionalOrElseGetTest {
    @Test
    public void test_basic() {
        fix().addInputLines(
                        "Test.java",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static String f(Optional<String> foo) {",
                        "    return foo.orElse(\"bar\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + Optional.class.getCanonicalName() + ";",
                        "class Test {",
                        "  static String f(Optional<String> foo) {",
                        "    return foo.orElseGet(() -> \"bar\");",
                        "  }",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(OptionalOrElseGet.class, getClass());
    }
}
