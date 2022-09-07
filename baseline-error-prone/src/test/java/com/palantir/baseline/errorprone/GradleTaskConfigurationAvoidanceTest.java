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

public class GradleTaskConfigurationAvoidanceTest {

    private static final String ERROR = "BUG: Diagnostic contains: " + GradleTaskConfigurationAvoidance.MESSAGE;

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(GradleTaskConfigurationAvoidance.class, getClass());
    }

    @Test
    public void fails_for_task() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.task(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_create() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().create(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_getByPath() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().getByPath(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_findByPath() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().findByPath(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void succeeds_for_register() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    project.getTasks().register(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_getByName() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().getByName(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_whenTaskAdded() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().whenTaskAdded(t -> {});",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_getAt() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().getAt(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_withType_action() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Task;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().withType(Task.class, t -> {",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_withType_closure() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import groovy.lang.Closure;",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Task;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().withType(Task.class, new Closure<Void>(null) {",
                        "      @Override",
                        "      public Void call() {",
                        "        return null;",
                        "      }",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void succeeds_for_withType_class() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Task;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    project.getTasks().withType(Task.class).configureEach(t -> {",
                        "    });",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_all() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().all(t -> {});",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_whenObjectAdded() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().whenObjectAdded(t -> {});",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void fails_for_findByName() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "class Foo {",
                        "  public final void apply(Project project) {",
                        "    // " + ERROR,
                        "    project.getTasks().findByName(\"name\");",
                        "  }",
                        "}")
                .doTest();
    }
}
