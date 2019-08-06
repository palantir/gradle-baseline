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

package com.palantir.baseline.refaster;

import org.junit.Test;

public class ExecutorSubmitRunnableFutureIgnoredTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(ExecutorSubmitRunnableFutureIgnored.class)
                .withInputLines(
                        "Test",
                        "import java.util.concurrent.Executors;",
                        "public class Test {",
                        "  static {",
                        "    Executors.newSingleThreadExecutor().submit(() -> {});",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import java.util.concurrent.Executors;",
                        "public class Test {",
                        "  static {",
                        "    Executors.newSingleThreadExecutor().execute(() -> {});",
                        "  }",
                        "}");
    }

    @Test
    public void testConsumedFutureUnchanged() {
        RefasterTestHelper
                .forRefactoring(ExecutorSubmitRunnableFutureIgnored.class)
                .withInputLines(
                        "Test",
                        "import java.util.concurrent.Executors;",
                        "import java.util.concurrent.Future;",
                        "public class Test {",
                        "  Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {});",
                        "}")
                .hasOutputLines(
                        "import java.util.concurrent.Executors;",
                        "import java.util.concurrent.Future;",
                        "public class Test {",
                        "  Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {});",
                        "}");
    }

}
