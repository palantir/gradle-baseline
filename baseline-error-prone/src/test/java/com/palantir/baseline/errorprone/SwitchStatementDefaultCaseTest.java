/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class SwitchStatementDefaultCaseTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(SwitchStatementDefaultCase.class, getClass());
    }

    @Test
    public void testDefaultCase() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  enum Case { ONE, TWO, THREE }",
                        "  void m(Case c) {",
                        "    // BUG: Diagnostic contains: Avoid using default case in switch statement.",
                        "    switch (c) {",
                        "      case ONE:",
                        "      case TWO:",
                        "      case THREE:",
                        "      default:",
                        "        break;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testNoDefaultCase() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  enum Case { ONE, TWO, THREE }",
                        "  void m(Case c) {",
                        "    switch (c) {",
                        "      case ONE:",
                        "      case TWO:",
                        "      case THREE:",
                        "        break;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }
}
