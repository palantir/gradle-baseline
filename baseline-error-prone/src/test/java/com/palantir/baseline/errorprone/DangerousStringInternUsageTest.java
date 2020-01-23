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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DangerousStringInternUsageTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousStringInternUsage.class, getClass());
    }

    @Test
    public void should_warn_when_parallel_with_no_arguments_is_invoked_on_subclass_of_java_stream() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "class Test {",
                        "   String f() {",
                        "       // BUG: Diagnostic contains: Should not use String.intern().",
                        "       return getClass().getName().intern();",
                        "   }",
                        "}")
                .doTest();
    }
}
