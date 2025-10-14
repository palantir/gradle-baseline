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

package com.palantir.baseline.tasks;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;

public abstract class CheckJUnitDependencies extends DefaultTask {
    @Input
    public abstract ListProperty<String> getErrorMessages();

    public CheckJUnitDependencies() {
        setGroup("Verification");
        setDescription("Ensures the correct JUnit4/5 dependencies are present, otherwise tests may silently not run");

        getErrorMessages().set(getProject().provider(this::validateDependencies));
    }

    @TaskAction
    public final void action() {
        List<String> errorMessages = getErrorMessages().get();

        if (errorMessages.isEmpty()) {
            return;
        }

        throw new IllegalStateException(
                "There were %s issues:\n\n".formatted(errorMessages.size()) + String.join("\n\n", errorMessages));
    }

    private List<String> validateDependencies() {
        List<String> errorMessages = new ArrayList<>();

        getProject()
                .getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .forEach(sourceSet -> {
                    Test task = getProject().getTasks().withType(Test.class).findByName(sourceSet.getName());
                    if (task == null) {
                        return;
                    }

                    getLogger().info("Analyzing source set {} with task {}", sourceSet.getName(), task.getName());

                    try {
                        validateSourceSet(sourceSet, task);
                    } catch (IllegalStateException e) {
                        errorMessages.add(e.getMessage());
                    }
                });

        return errorMessages;
    }

    @SuppressWarnings("CyclomaticComplexity")
    private void validateSourceSet(SourceSet sourceSet, Test task) throws IllegalStateException {
        Set<ResolvedComponentResult> deps = getProject()
                .getConfigurations()
                .getByName(sourceSet.getRuntimeClasspathConfigurationName())
                .getIncoming()
                .getResolutionResult()
                .getAllComponents();

        boolean usesJUnit4 = usesApi(sourceSet, CheckJUnitDependencies::isJUnit4);
        boolean usesJUnit5 = usesApi(sourceSet, CheckJUnitDependencies::isJUnit5);
        boolean usesJqwik = usesApi(sourceSet, CheckJUnitDependencies::isJqwik);

        boolean hasJunitJupiter = hasDep(deps, CheckJUnitDependencies::isJunitJupiter);
        boolean hasVintageEngine = hasDep(deps, CheckJUnitDependencies::isVintageEngine);
        boolean hasJqwikEngine = hasDep(deps, CheckJUnitDependencies::isJqwikEngine);
        boolean hasSpock = hasDep(deps, CheckJUnitDependencies::isSpock);

        TestFrameworkOptions options = task.getTestFramework().getOptions();

        if (options instanceof JUnitPlatformOptions) {
            if (usesJUnit4) {
                Preconditions.checkState(
                        hasVintageEngine, """
                        Some tests use JUnit4, but the '%s' task is not using the JUnit Vintage engine. \
                        To ensure your JUnit4 tests are run, add the following dependency:

                        %s 'org.junit.vintage:junit-vintage-engine'

                        """, task.getName(), sourceSet.getRuntimeOnlyConfigurationName());
            }

            if (usesJUnit5) {
                Preconditions.checkState(
                        hasJunitJupiter, """
                        Some tests use JUnit5, but the '%s' task is not using the JUnit Jupiter engine. \
                        To ensure your JUnit5 tests are run, add the following dependency:

                        %s 'org.junit.jupiter:junit-jupiter'

                        """, task.getName(), sourceSet.getRuntimeOnlyConfigurationName());
            }

            if (usesJqwik) {
                Preconditions.checkState(
                        hasJqwikEngine, """
                        Some tests use Jqwik, but the '%s' task is not using the Jqwik engine. \
                        To ensure your Jqwik tests are run, add the following dependency:

                        %s 'net.jqwik:jqwik-engine'

                        """, task.getName(), sourceSet.getRuntimeOnlyConfigurationName());
            }
        } else {
            if (usesJUnit5) {
                throw new IllegalStateException(String.format("""
                    Some tests use JUnit5, but the '%s' task is not using using the JUnit Platform test \
                    framework.\
                    """, task.getName()));
            }

            if (usesJqwik) {
                throw new IllegalStateException(String.format("""
                    Some tests use Jqwik, but the '%s' task is not using using the JUnit Platform test \
                    framework.\
                    """, task.getName()));
            }

            if (hasSpock) {
                throw new IllegalStateException(String.format("""
                    Some tests use Spock, but the '%s' task is not using using the JUnit Platform test \
                    framework.\
                    """, task.getName()));
            }
        }
    }

    private static boolean usesApi(SourceSet sourceSet, Predicate<String> condition) {
        // getAllJava() includes groovy sources too
        return !sourceSet
                .getAllJava()
                .filter(file -> {
                    try (Stream<String> lines = Files.lines(file.toPath())) {
                        return lines.anyMatch(condition);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .isEmpty();
    }

    private static boolean isJUnit4(String line) {
        return line.contains("org.junit.Test")
                || line.contains("org.junit.runner")
                || line.contains("org.junit.ClassRule");
    }

    private static boolean isJUnit5(String line) {
        return line.contains("org.junit.jupiter.api.");
    }

    private static boolean isJqwik(String line) {
        return line.contains("net.jqwik.api.");
    }

    private static boolean hasDep(Set<ResolvedComponentResult> deps, Predicate<ModuleVersionIdentifier> spec) {
        return deps.stream().anyMatch(component -> spec.test(component.getModuleVersion()));
    }

    private static boolean isJunitJupiter(ModuleVersionIdentifier dep) {
        return "org.junit.jupiter".equals(dep.getGroup()) && "junit-jupiter".equals(dep.getName());
    }

    private static boolean isVintageEngine(ModuleVersionIdentifier dep) {
        return "org.junit.vintage".equals(dep.getGroup()) && "junit-vintage-engine".equals(dep.getName());
    }

    private static boolean isJqwikEngine(ModuleVersionIdentifier dep) {
        return "net.jqwik".equals(dep.getGroup()) && "jqwik-engine".equals(dep.getName());
    }

    private static boolean isSpock(ModuleVersionIdentifier dep) {
        return "org.spockframework".equals(dep.getGroup()) && "spock-core".equals(dep.getName());
    }
}
