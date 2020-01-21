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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class OptionalOrElseThrowThrowsTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(OptionalOrElseThrowThrows.class, getClass());
    }

    @Test
    public void testMethodReference() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  public <T> T foo(Optional<T> optional) {",
                        "    return optional.orElseThrow(this::getThrowable);",
                        "  }",
                        "  private RuntimeException getThrowable() {",
                        "    return new RuntimeException();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testReturns_block() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  public <T> T foo(Optional<T> optional) {",
                        "    return optional.orElseThrow(() -> { return new RuntimeException(\"not present\"); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testReturns_lambda() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  public <T> T foo(Optional<T> optional) {",
                        "    return optional.orElseThrow(() -> new RuntimeException(\"not present\"));",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testOptional_throws_block() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.Optional;",
                        "class Test {",
                        "  public <T> T foo(Optional<T> optional) {",
                        "    // BUG: Diagnostic contains: orElseThrow argument must return an exception, not throw one",
                        "    return optional.orElseThrow(() -> { throw new RuntimeException(\"not present\"); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testOptionalInt_throws_block() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.OptionalInt;",
                        "class Test {",
                        "  public int foo(OptionalInt optional) {",
                        "    // BUG: Diagnostic contains: orElseThrow argument must return an exception, not throw one",
                        "    return optional.orElseThrow(() -> { throw new RuntimeException(\"not present\"); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testOptionalDouble_throws_block() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.OptionalDouble;",
                        "class Test {",
                        "  public double foo(OptionalDouble optional) {",
                        "    // BUG: Diagnostic contains: orElseThrow argument must return an exception, not throw one",
                        "    return optional.orElseThrow(() -> { throw new RuntimeException(\"not present\"); });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testOptionalLong_throws_block() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.util.OptionalLong;",
                        "class Test {",
                        "  public long foo(OptionalLong optional) {",
                        "    // BUG: Diagnostic contains: orElseThrow argument must return an exception, not throw one",
                        "    return optional.orElseThrow(() -> { throw new RuntimeException(\"not present\"); });",
                        "  }",
                        "}")
                .doTest();
    }
}
