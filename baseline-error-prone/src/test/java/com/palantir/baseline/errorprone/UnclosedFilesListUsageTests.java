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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public final class UnclosedFilesListUsageTests {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(UnclosedFilesListUsage.class, getClass());
    }

    @Test
    public void testThrowsOnNoTryWithResources() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.nio.file.Files;",
                        "import java.nio.file.Paths;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "      // BUG: Diagnostic contains: must be used within a try-with-resources block",
                        "      Files.list(Paths.get(\"/tmp\"));",
                        "    } catch (java.io.IOException e) {",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void positive() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.nio.file.Files;",
                        "import java.nio.file.Path;",
                        "import java.nio.file.Paths;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (Stream<Path> files = Files.list(Paths.get(\"/tmp\"))) {",
                        "    } catch (java.io.IOException e) {",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

}
