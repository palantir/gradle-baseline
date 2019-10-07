/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ExecutorSubmitRunnableFutureIgnoredTest {

    @Test
    void testFix() {
        BugCheckerRefactoringTestHelper.newInstance(new ExecutorSubmitRunnableFutureIgnored(), getClass())
                .addInputLines(
                        "Test.java",
                        "import " + ExecutorService.class.getName() + ';',
                        "class Test {",
                        "   void f(ExecutorService exec) {",
                        "       exec.submit(() -> System.out.println(\"Hello\"));",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import " + ExecutorService.class.getName() + ';',
                        "class Test {",
                        "   void f(ExecutorService exec) {",
                        "       exec.execute(() -> System.out.println(\"Hello\"));",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testNegative() {
        CompilationTestHelper.newInstance(ExecutorSubmitRunnableFutureIgnored.class, getClass()).addSourceLines(
                "Test.java",
                "import " + ExecutorService.class.getName() + ';',
                "import " + Future.class.getName() + ';',
                "class Test {",
                "   void f(ExecutorService exec) {",
                "       Future<?> future = exec.submit(() -> System.out.println(\"Hello\"));",
                "   }",
                "}").doTest();
    }

    @Test
    void testMessage() {
        CompilationTestHelper.newInstance(ExecutorSubmitRunnableFutureIgnored.class, getClass()).addSourceLines(
                "Test.java",
                "import " + ExecutorService.class.getName() + ';',
                "class Test {",
                "   void f(ExecutorService exec) {",
                "       // BUG: Diagnostic contains: not logged by the uncaught exception handler",
                "       exec.submit(() -> System.out.println(\"Hello\"));",
                "   }",
                "}").doTest();
    }
}
