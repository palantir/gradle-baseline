/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

public class NoWarmupRateLimiterTest {

    @Test
    public void should_explicitly_initialize_warmup() {
        getRefactoringValidatorHelper()
                .addInputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10);",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.time.Duration;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10, Duration.ZERO);",
                        "  }",
                        "",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void should_explicitly_initialize_warmup_import_exists() {
        getRefactoringValidatorHelper()
                .addInputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.time.Duration;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10);",
                        "  }",
                        "",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.time.Duration;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10, Duration.ZERO);",
                        "  }",
                        "",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    public void pass_explicitly_initialize_warmup_single_arg() {
        getCompilationTestHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.time.Duration;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10, Duration.ZERO);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    @Test
    public void pass_explicitly_initialize_warmup_double_args() {
        getCompilationTestHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.util.concurrent.TimeUnit;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10, 100, TimeUnit.SECONDS);",
                        "  }",
                        "",
                        "}")
                .doTest();
    }

    private RefactoringValidator getRefactoringValidatorHelper() {
        return RefactoringValidator.of(NoWarmupRateLimiter.class, getClass());
    }

    private CompilationTestHelper getCompilationTestHelper() {
        return CompilationTestHelper.newInstance(NoWarmupRateLimiter.class, getClass());
    }
}
