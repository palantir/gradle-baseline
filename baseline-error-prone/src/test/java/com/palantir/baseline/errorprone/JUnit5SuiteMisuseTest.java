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

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class JUnit5SuiteMisuseTest {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(JUnit5SuiteMisuse.class, getClass());
    }

    @Test
    public void multiple_junit4_references_pass() {
        compilationHelper
                .addSourceLines(
                        "Container.java",
                        "import org.junit.runner.RunWith;",
                        "import org.junit.runners.Suite;",
                        "class Container {",
                        "  @RunWith(Suite.class)",
                        "  @Suite.SuiteClasses({FooTest.class, BarTest.class})",
                        "  public static class MySuite {}",
                        "",
                        "  public static class FooTest {",
                        "    @org.junit.Test public void my_test() {}",
                        "  }",
                        "",
                        "  public static class BarTest {",
                        "    @org.junit.Test public void my_test() {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void single_junit4_reference_passes() {
        compilationHelper
                .addSourceLines(
                        "Container.java",
                        "import org.junit.runner.RunWith;",
                        "import org.junit.runners.Suite;",
                        "class Container {",
                        "  @RunWith(Suite.class)",
                        "  @Suite.SuiteClasses(FooTest.class)",
                        "  public static class MySuite {}",
                        "",
                        "  public static class FooTest {",
                        "    @org.junit.Test public void my_test() {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void single_junit4_reference_passes_different_order() {
        compilationHelper
                .addSourceLines(
                        "Container.java",
                        "import org.junit.runner.RunWith;",
                        "import org.junit.runners.Suite;",
                        "class Container {",
                        "  public static class FooTest {",
                        "    @org.junit.Test public void my_test() {}",
                        "  }",
                        "",
                        "  @RunWith(Suite.class)",
                        "  @Suite.SuiteClasses(FooTest.class)",
                        "  public static class MySuite {}",
                        "}")
                .doTest();
    }

    @Test
    public void multiple_junit5_references_fail() {
        compilationHelper
                .addSourceLines(
                        "Container.java",
                        "import org.junit.runner.RunWith;",
                        "import org.junit.runners.Suite;",
                        "class Container {",
                        "  @RunWith(Suite.class)",
                        "  @Suite.SuiteClasses({FooTest.class, BarTest.class})",
                        "  public static class MySuite {}",
                        "",
                        "  // BUG: Diagnostic contains: JUnit5SuiteMisuse",
                        "  public static class FooTest {",
                        "    @org.junit.jupiter.api.Test public void my_test() {}",
                        "  }",
                        "",
                        "  // BUG: Diagnostic contains: JUnit5SuiteMisuse",
                        "  public static class BarTest {",
                        "    @org.junit.jupiter.api.Test public void my_test() {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void multiple_junit5_references_fail_different_order() {
        compilationHelper
                .addSourceLines(
                        "Container.java",
                        "import org.junit.runner.RunWith;",
                        "import org.junit.runners.Suite;",
                        "class Container {",
                        "  public static class FooTest {",
                        "    @org.junit.jupiter.api.Test public void my_test() {}",
                        "  }",
                        "",
                        "  public static class BarTest {",
                        "    @org.junit.jupiter.api.Test public void my_test() {}",
                        "  }",
                        "",
                        "  @RunWith(Suite.class)",
                        "  // BUG: Diagnostic contains: JUnit5SuiteMisuse",
                        "  @Suite.SuiteClasses({FooTest.class, BarTest.class})",
                        "  public static class MySuite {}",
                        "}")
                .doTest();
    }

    @Test
    public void single_junit5_reference_fails() {
        compilationHelper
                .addSourceLines(
                        "Container.java",
                        "import org.junit.runner.RunWith;",
                        "import org.junit.runners.Suite;",
                        "class Container {",
                        "  @RunWith(Suite.class)",
                        "  @Suite.SuiteClasses(FooTest.class)",
                        "  public static class MySuite {}",
                        "",
                        "  // BUG: Diagnostic contains: JUnit5SuiteMisuse",
                        "  public static class FooTest {",
                        "    @org.junit.jupiter.api.Test public void my_test() {}",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void single_junit5_reference_fails_different_order() {
        compilationHelper
                .addSourceLines(
                        "Container.java",
                        "import org.junit.runner.RunWith;",
                        "import org.junit.runners.Suite;",
                        "class Container {",
                        "  public static class FooTest {",
                        "    @org.junit.jupiter.api.Test public void my_test() {}",
                        "  }",
                        "",
                        "  @RunWith(Suite.class)",
                        "  // BUG: Diagnostic contains: JUnit5SuiteMisuse",
                        "  @Suite.SuiteClasses(FooTest.class)",
                        "  public static class MySuite {}",
                        "}")
                .doTest();
    }
}
