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
public final class DangerousCompletableFutureUsageTest {
    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousCompletableFutureUsage.class, getClass());
    }

    @Test
    public void should_fail_without_executor_supplyAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       // BUG: Diagnostic contains: Should not use CompletableFuture methods without specifying",
                "       CompletableFuture.supplyAsync(() -> 1L);",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    public void should_pass_with_executor_supplyAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "import java.util.concurrent.Executors;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       CompletableFuture.supplyAsync(() -> 1L, Executors.newCachedThreadPool());",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    public void should_fail_without_executor_runAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       // BUG: Diagnostic contains: Should not use CompletableFuture methods without specifying",
                "       CompletableFuture.runAsync(() -> {});",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    public void should_pass_with_executor_runAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "import java.util.concurrent.Executors;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       CompletableFuture.runAsync(() -> {}, Executors.newCachedThreadPool());",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    public void should_fail_without_executor_thenApplyAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       CompletableFuture<Integer> future = CompletableFuture.completedFuture(1);",
                "       // BUG: Diagnostic contains: Should not use CompletableFuture methods without specifying",
                "       future.thenApplyAsync(i -> i);",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    public void should_pass_with_executor_thenApplyAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "import java.util.concurrent.Executors;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       CompletableFuture.completedFuture(1).thenApplyAsync(i -> i, Executors.newCachedThreadPool());",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    public void should_fail_without_executor_applyToEitherAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       CompletableFuture<Integer> futureOne = CompletableFuture.completedFuture(1);",
                "       CompletableFuture<Integer> futureTwo = CompletableFuture.completedFuture(2);",
                "       // BUG: Diagnostic contains: Should not use CompletableFuture methods without specifying",
                "       futureOne.applyToEitherAsync(futureTwo, i -> i);",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    public void should_pass_with_executor_applyToEitherAsync() {
        compilationHelper.addSourceLines(
                "Test.java",
                "import java.util.concurrent.CompletableFuture;",
                "import java.util.concurrent.Executors;",
                "class Test {",
                "   public static final void main(String[] args) {",
                "       CompletableFuture<Integer> futureOne = CompletableFuture.completedFuture(1);",
                "       CompletableFuture<Integer> futureTwo = CompletableFuture.completedFuture(2);",
                "       futureOne.applyToEitherAsync(futureTwo, i -> i, Executors.newCachedThreadPool());",
                "   }",
                "}"
        ).doTest();
    }
}
