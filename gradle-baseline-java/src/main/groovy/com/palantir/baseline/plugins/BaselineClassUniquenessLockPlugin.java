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

package com.palantir.baseline.plugins;

import com.palantir.baseline.tasks.ClassUniquenessAnalyzer;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.provider.SetProperty;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.GFileUtils;

/**
 * This plugin is similar to https://github.com/nebula-plugins/gradle-lint-plugin/wiki/Duplicate-Classes-Rule but goes
 * one step further and actually hashes any identically named classfiles to figure out if they're <i>completely</i>
 * identical (and therefore safely interchangeable).
 *
 * <p>The task only fails if it finds classes which have the same name but different implementations.
 */
public class BaselineClassUniquenessLockPlugin extends AbstractBaselinePlugin {
    @Override
    public final void apply(Project project) {

        // TODO(dfox): expose this so that users can add/remove their own configurations?
        SetProperty<String> configurationNames = project.getObjects().setProperty(String.class);
        project.getPlugins().withId("java", plugin -> {
            configurationNames.add("runtimeClasspath");
        });

        File lockFile = project.file("baseline-class-uniqueness.lock");

        // TODO(dfox): up-to-dateness of this task
        Task checkClassUniquenessLock = project.getTasks().create("checkClassUniquenessLock");
        checkClassUniquenessLock.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                Map<String, Optional<String>> resultsByConfiguration = configurationNames.get().stream()
                        .collect(Collectors.toMap(Function.identity(), configurationName -> {
                            ClassUniquenessAnalyzer analyzer = new ClassUniquenessAnalyzer(project.getLogger());
                            Configuration configuration = project.getConfigurations().getByName(configurationName);
                            analyzer.analyzeConfiguration(configuration);
                            Collection<Set<ModuleVersionIdentifier>> problemJars = analyzer.getDifferingProblemJars();

                            if (problemJars.isEmpty()) {
                                return Optional.empty();
                            }

                            StringBuilder stringBuilder = new StringBuilder();
                            // TODO(dfox): ensure we iterate through problemJars in a stable order
                            for (Set<ModuleVersionIdentifier> clashingJars : problemJars) {
                                stringBuilder
                                        .append(clashingJars.stream()
                                                .map(mvi -> mvi.getGroup() + ":" + mvi.getName())
                                                .sorted()
                                                .collect(Collectors.joining(", ", "[", "]")))
                                        .append('\n');

                                analyzer.getDifferingSharedClassesInProblemJars(clashingJars).stream()
                                        .sorted()
                                        .forEach(className -> {
                                            stringBuilder.append("  - ");
                                            stringBuilder.append(className);
                                            stringBuilder.append('\n');
                                        });
                            }
                            return Optional.of(stringBuilder.toString());
                        }));

                boolean conflictsFound = resultsByConfiguration.values().stream().anyMatch(Optional::isPresent);
                if (!conflictsFound) {
                    ensureLockfileDoesNotExist();
                } else {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(
                            "# Run ./gradlew checkClassUniquenessLock --write-locks to update this " + "file\n\n");
                    resultsByConfiguration.forEach((name, contents) -> {
                        if (contents.isPresent()) {
                            stringBuilder.append("## ").append(name).append("\n");
                            stringBuilder.append(contents.get());
                        }
                    });
                    ensureLockfileContains(stringBuilder.toString());
                }
            }

            private void ensureLockfileContains(String expected) {
                if (project.getGradle().getStartParameter().isWriteDependencyLocks()) {
                    GFileUtils.writeFile(expected, lockFile);
                } else {
                    String onDisk = GFileUtils.readFile(lockFile);
                    if (!onDisk.equals(expected)) {
                        throw new GradleException(lockFile
                                + " is out of date, please run `./gradlew "
                                + "checkClassUniquenessLock --write-locks` to update this file");
                    }
                }
            }

            private void ensureLockfileDoesNotExist() {
                if (project.getGradle().getStartParameter().isWriteDependencyLocks()) {
                    GFileUtils.deleteQuietly(lockFile);
                } else {
                    if (!lockFile.exists()) {
                        throw new GradleException(lockFile + " should not exist (as no problems were found).");
                    }
                }
            }
        });

        project.getPlugins().apply(LifecycleBasePlugin.class);
        project.getTasks().getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(checkClassUniquenessLock);
    }
}
