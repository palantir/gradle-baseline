/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

public class OptionalToStringUsageTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(OptionalToString.class, getClass());
    }

    @Test
    public void should_throw_error_if_to_string_is_invoked_on_java_optional() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "   String f() {",
                        "       // BUG: Diagnostic contains: Optional.toString() does not stringifies the value",
                        "       return Optional.of(\"This is an optional value\").toString();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    public void should_throw_error_if_to_string_is_invoked_on_guava_optional() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.base.Optional;",
                        "class Test {",
                        "   String f() {",
                        "       // BUG: Diagnostic contains: Optional.toString() does not stringifies the value",
                        "       return Optional.of(\"This is an optional value\").toString();",
                        "   }",
                        "}")
                .doTest();
    }
}
