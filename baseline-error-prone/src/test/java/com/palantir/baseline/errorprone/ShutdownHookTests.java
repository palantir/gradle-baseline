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
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class ShutdownHookTests {

    private static final String errorMsg = "BUG: Diagnostic contains: "
            + "Use your webserver's managed resource functionality "
            + "instead of using java.lang.Runtime#addShutdownHook directly.";

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(ShutdownHook.class, getClass());
    }

    @Test
    public void testUsesShutdownHooks() {
        test("addShutdownHook(new Thread())", Optional.of(errorMsg));
        test("removeShutdownHook(new Thread())", Optional.of(errorMsg));
    }

    @Test
    public void testDoesNotUseShutdownHooks() {
        test("availableProcessors()", Optional.empty());
    }

    private void test(String method, Optional<String> error) {
        compilationHelper.addSourceLines(
                "Test.java",
                "class Test {",
                "  void f(String param) {",
                "// " + error.orElse(""),
                "    Runtime.getRuntime()." + method + ";",
                "  }",
                "}"
        ).doTest();
    }
}
