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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class UnusedParameterTest {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(UnusedParameter.class, getClass());
    }

    @Test
    public void handles_interface() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.Optional;",
                "interface Test {",
                "  void method(String param);",
                "  // BUG: Diagnostic contains: Unused",
                "  default void defaultMethod(String param) { }",
                "}").doTest();
    }

    @Test
    public void handles_abstract_classes() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.Optional;",
                "abstract class Test {",
                "  abstract void method(String param);",
                "  // BUG: Diagnostic contains: Unused",
                "  void defaultMethod(String param) { }",
                "  // BUG: Diagnostic contains: Unused",
                "  private void privateMethod(String param) { }",
                "}").doTest();
    }

    @Test
    public void handles_classes() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.Optional;",
                "class Test {",
                "  // BUG: Diagnostic contains: Unused",
                "  private static void privateMethod(String buggy) { }",
                "  // BUG: Diagnostic contains: Unused",
                "  public static void publicMethod(String buggy) { }",
                "}").doTest();
    }

}
