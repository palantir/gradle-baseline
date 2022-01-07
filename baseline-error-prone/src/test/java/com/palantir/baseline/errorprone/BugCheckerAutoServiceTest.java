/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.Test;

class BugCheckerAutoServiceTest {

    private CompilationTestHelper compilationHelper() {
        return CompilationTestHelper.newInstance(BugCheckerAutoService.class, getClass());
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(BugCheckerAutoService.class, getClass());
    }

    @Test
    public void doingItRight() {
        compilationHelper()
                .addSourceLines(
                        "TestChecker.java",
                        "import com.google.auto.service.AutoService;",
                        "import com.google.errorprone.BugPattern;",
                        "import com.google.errorprone.bugpatterns.BugChecker;",
                        "import com.google.errorprone.BugPattern.SeverityLevel;",
                        "@AutoService(BugChecker.class)",
                        "@BugPattern(name=\"Test\", summary=\"a\", severity=SeverityLevel.ERROR)",
                        "public class TestChecker extends BugChecker {",
                        "}")
                .doTest();
    }

    @Test
    public void nonCheckerAutoService() {
        compilationHelper()
                .addSourceLines(
                        "Test.java",
                        "import com.google.auto.service.AutoService;",
                        "@AutoService(Runnable.class)",
                        "public class Test implements Runnable {",
                        "  public void run() {}",
                        "}")
                .doTest();
    }

    @Test
    public void fixBugChecker() {
        fix().addInputLines(
                        "TestChecker.java",
                        "import com.google.auto.service.AutoService;",
                        "import com.google.errorprone.BugPattern;",
                        "import com.google.errorprone.bugpatterns.BugChecker;",
                        "import com.google.errorprone.BugPattern.SeverityLevel;",
                        "@BugPattern(name=\"Test\", summary=\"a\", severity=SeverityLevel.ERROR)",
                        "public class TestChecker extends BugChecker {",
                        "}")
                .addOutputLines(
                        "TestChecker.java",
                        "import com.google.auto.service.AutoService;",
                        "import com.google.errorprone.BugPattern;",
                        "import com.google.errorprone.bugpatterns.BugChecker;",
                        "import com.google.errorprone.BugPattern.SeverityLevel;",
                        "@AutoService(BugChecker.class)",
                        "@BugPattern(name=\"Test\", summary=\"a\", severity=SeverityLevel.ERROR)",
                        "public class TestChecker extends BugChecker {",
                        "}")
                .doTest();
    }
}
