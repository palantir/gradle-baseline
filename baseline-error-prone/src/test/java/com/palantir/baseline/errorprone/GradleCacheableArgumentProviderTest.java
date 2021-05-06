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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GradleCacheableArgumentProviderTest {

    private static final String errorMsg = "BUG: Diagnostic contains: "
            + "Gradle command line providers are not cacheable when implemented by lambdas";

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(GradleCacheableArgumentProvider.class, getClass());
    }

    @Test
    public void fails_for_compile_options_lambda_command_line_argument_provider() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import java.util.Collections;",
                        "import org.gradle.api.tasks.compile.CompileOptions;",
                        "class Foo {",
                        "  public final void apply(CompileOptions compileOptions) {",
                        "    // " + errorMsg,
                        "    compileOptions.getCompilerArgumentProviders().add(() -> {",
                        "      return Collections.singleton(\"I'm in a lambda\");",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_exec_task_lambda_command_line_argument_provider() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import java.util.Collections;",
                        "import org.gradle.api.tasks.Exec;",
                        "class Foo {",
                        "  public final void apply(Exec exec) {",
                        "    // " + errorMsg,
                        "    exec.getArgumentProviders().add(() -> {",
                        "      return Collections.singleton(\"I'm in a lambda\");",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void succeeds_for_exec_task_anon_class_command_line_argument_provider() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import java.util.Collections;",
                        "import org.gradle.api.tasks.Exec;",
                        "import org.gradle.process.CommandLineArgumentProvider;",
                        "class Foo {",
                        "  public final void apply(Exec exec) {",
                        "    exec.getArgumentProviders().add(new CommandLineArgumentProvider() {",
                        "      public Iterable<String> asArguments() {",
                        "        return Collections.singleton(\"I'm in a lambda\");",
                        "      }",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }
}
