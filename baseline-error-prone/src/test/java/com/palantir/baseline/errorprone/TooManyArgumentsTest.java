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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class TooManyArgumentsTest {

    @Test
    void detects_large_method() {
        compilationHelper()
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  // BUG: Diagnostic contains: [TooManyArguments] Interfaces can take at most",
                        "  public void foo(int a1, int a2, int a3, int a4, int a5, int a6, "
                                + "int a7, int a8, int a9, int a10, int a11) {}",
                        "}")
                .doTest();
    }

    @Test
    void ignores_regular_method() {
        compilationHelper()
                .addSourceLines("Test.java", "class Test {", "  public void foo(int a1) {}", "}")
                .doTest();
    }

    @Test
    void ignores_constructor() {
        compilationHelper()
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "  public Test(int a1, int a2, int a3, int a4, int a5, int a6, "
                                + "int a7, int a8, int a9, int a10, int a11) {}",
                        "}")
                .doTest();
    }

    @Test
    void ignores_implementations() {
        compilationHelper()
                .addSourceLines(
                        "BadInterface.java",
                        "interface BadInterface {",
                        "  // BUG: Diagnostic contains: [TooManyArguments] Interfaces can take at most",
                        "  void foo(int a1, int a2, int a3, int a4, int a5, int a6, "
                                + "int a7, int a8, int a9, int a10, int a11);",
                        "}")
                .addSourceLines(
                        "Test.java",
                        "import " + Override.class.getName() + ";",
                        "class Test implements BadInterface {",
                        "  @Override",
                        "  public void foo(int a1, int a2, int a3, int a4, int a5, int a6, "
                                + "int a7, int a8, int a9, int a10, int a11) {}",
                        "}")
                .doTest();
    }

    private CompilationTestHelper compilationHelper() {
        return CompilationTestHelper.newInstance(TooManyArguments.class, getClass());
    }
}
