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
        compilationHelper = CompilationTestHelper.newInstance(UnclosedFilesStreamUsage.class, getClass());
    }

    @Test
    public void testThrowsOnList_noTryWithResources() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.nio.file.Files;",
                        "import java.nio.file.Paths;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "      // BUG: Diagnostic contains: java.nio.file.Files must be called within a try-with",
                        "      Files.list(Paths.get(\"/tmp\"));",
                        "    } catch (java.io.IOException e) {",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testThrowsOnWalk_noTryWithResources() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.nio.file.Files;",
                        "import java.nio.file.Paths;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try {",
                        "      // BUG: Diagnostic contains: java.nio.file.Files must be called within a try-with",
                        "      Files.walk(Paths.get(\"/tmp\"));",
                        "    } catch (java.io.IOException e) {",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testThrowsOnWalk_aDifferentTryWithResources() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import java.io.IOException;",
                        "import java.io.StringWriter;",
                        "import java.io.Writer;",
                        "import java.nio.file.Files;",
                        "import java.nio.file.Path;",
                        "import java.nio.file.Paths;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "  void f(String param) {",
                        "    try (Writer writer = new StringWriter()) {",
                        "      // BUG: Diagnostic contains: java.nio.file.Files must be called within a try-with",
                        "      Stream<Path> files = Files.list(Paths.get(\"/tmp\"));",
                        "      files.forEach(path -> {",
                        "        try { writer.write(path.toString()); }",
                        "        catch (IOException e) {}",
                        "      });",
                        "    } catch (IOException e) {}",
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
