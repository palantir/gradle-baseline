/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
