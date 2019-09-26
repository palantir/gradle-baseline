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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class GradleCacheableTaskActionTest {

    private static final String errorMsg = "BUG: Diagnostic contains: "
            + "Gradle task actions are not cacheable when implemented by lambdas";

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(GradleCacheableTaskAction.class, getClass());
    }

    @Test
    public void failsOnDoFirstLambda() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Task;",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Plugin;",
                        "class Foo implements Plugin<Project> {",
                        "  public final void apply(Project project) {",
                        "    project.getTasks().register(\"foo\", task -> {",
                        "      // " + errorMsg,
                        "      task.doFirst(t -> System.out.println(\"im a lambda\"));",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void allowsDoFirstAnonymousClass() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Task;",
                        "import org.gradle.api.Action;",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Plugin;",
                        "class Foo implements Plugin<Project> {",
                        "  public final void apply(Project project) {",
                        "    project.getTasks().register(\"foo\", task -> {",
                        "      task.doFirst(new Action<Task>() {",
                        "          public void execute(Task task) {}",
                        "      });",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }
}
