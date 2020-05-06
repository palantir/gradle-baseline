/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

public class GradleProviderToStringTest {

    private CompilationTestHelper compilationHelper;
    private RefactoringValidator refactoringValidator;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(GradleProviderToString.class, getClass());
        refactoringValidator = RefactoringValidator.of(new GradleProviderToString(), getClass());
    }

    @Test
    public void failsUsedInStringConcatenation() {
        compilationHelper
                .addSourceLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Plugin;",
                        "import org.gradle.api.provider.Provider;",
                        "class Foo implements Plugin<Project> {",
                        "  public final void apply(Project project) {",
                        "    String nonProvider = \"foo\"",
                        "    Provider<String> provider = project.provider(() -> \"hello\");",
                        "    // BUG: Diagnostic contains: Calling toString on a Provider",
                        "    String value = \"My bad provider value: \" + provider + nonProvider;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void failsUsedInStringConcatenation_replacesWithGet() {
        refactoringValidator
                .addInputLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Plugin;",
                        "import org.gradle.api.provider.Provider;",
                        "class Foo implements Plugin<Project> {",
                        "  public final void apply(Project project) {",
                        "    Provider<String> provider = project.provider(() -> \"hello\");",
                        "    String value = \"My bad provider value: \" + provider;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Foo.java",
                        "import org.gradle.api.Project;",
                        "import org.gradle.api.Plugin;",
                        "import org.gradle.api.provider.Provider;",
                        "class Foo implements Plugin<Project> {",
                        "  public final void apply(Project project) {",
                        "    Provider<String> provider = project.provider(() -> \"hello\");",
                        "    String value = \"My bad provider value: \" + provider.get();",
                        "  }",
                        "}")
                .doTest();
    }
}
