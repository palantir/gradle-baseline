/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public final class DangerousThreadPoolExecutorUsageTests {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousThreadPoolExecutorUsage.class, getClass());
    }

    @Test
    public void testThrowsOnThreadPoolExecutor() {
        compilationHelper
                .addSourceLines(
                        "Bean.java",
                        "import java.util.concurrent.ThreadPoolExecutor;",
                        "import java.util.concurrent.ExecutorService;",
                        "class Bean {",
                        "// BUG: Diagnostic contains: Should not normally use ThreadPoolExecutor directly.",
                        "ExecutorService executor = new ThreadPoolExecutor(1, 1, 1, null, null);",
                        "}")
                .doTest();
    }

    @Test
    public void testDoesNotThrowWithoutThreadPoolExecutor() {
        compilationHelper
                .addSourceLines("Bean.java", "class Bean {", "String value = null;", "}")
                .doTest();
    }
}
