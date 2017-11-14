/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public final class DangerousExecutorUsageTests {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousExecutorUsage.class, getClass());
    }

    @Test
    public void testThrowsOnThreadPoolExecutor() {
        compilationHelper.addSourceLines(
                "Bean.java",
                "import java.util.concurrent.ThreadPoolExecutor;",
                "import java.util.concurrent.ExecutorService;",
                "class Bean {",
                "// BUG: Diagnostic contains: Should not normally use ThreadPoolExecutor directly.",
                "ExecutorService executor = new ThreadPoolExecutor(1, 1, 1, null, null);",
                "}").doTest();
    }

    @Test
    public void testDoesNotThrowWithoutThreadPoolExecutor() {
        compilationHelper.addSourceLines(
                "Bean.java",
                "class Bean {",
                "String value = null;",
                "}").doTest();
    }
}
