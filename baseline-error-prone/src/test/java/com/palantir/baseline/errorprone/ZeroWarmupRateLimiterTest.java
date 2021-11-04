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

import org.junit.jupiter.api.Test;

class ZeroWarmupRateLimiterTest {

    @Test
    public void should_remove_duration_zero() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.time.Duration;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10, Duration.ZERO);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.time.Duration;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void should_remove_duration_zero_static_import() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import static java.time.Duration.ZERO;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10, ZERO);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import static java.time.Duration.ZERO;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void should_not_modify_existing_uses() {
        fix().addInputLines(
                        "Test.java",
                        "import com.google.common.util.concurrent.RateLimiter;",
                        "import java.time.Duration;",
                        "class Test {",
                        "  void f() {",
                        "    RateLimiter.create(10, Duration.ofMillis(100));",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(ZeroWarmupRateLimiter.class, getClass());
    }
}
