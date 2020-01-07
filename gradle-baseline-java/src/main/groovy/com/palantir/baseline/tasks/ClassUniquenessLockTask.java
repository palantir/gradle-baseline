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

package com.palantir.baseline.tasks;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GFileUtils;

public class ClassUniquenessLockTask extends DefaultTask {
    @OutputFile public final File lockFile;

    // not marking this as an Input, because we want to re-run if the *contents* of a configuration changes
    public final SetProperty<String> configurations;

    public ClassUniquenessLockTask() {
        this.configurations = getProject().getObjects().setProperty(String.class);
        this.lockFile = getProject().file("baseline-class-uniqueness.lock");
        onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                return !configurations.get().isEmpty();
            }
        });
    }

    /**
     * This method exists purely for up-to-dateness purposes - we want to re-run if the contents of a configuration
     * changes.
     */
    @Input
    public Map<String, List<ModuleVersionIdentifier>> contentsOfAllConfigurations() {
        return configurations.get().stream().collect(Collectors.toMap(Function.identity(), name -> {
            Configuration configuration = getProject().getConfigurations().getByName(name);
            return configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                    .map(ResolvedComponentResult::getModuleVersion)
                    .collect(Collectors.toList());
        }));
    }

    @TaskAction
    public void doIt() {
        Map<String, Optional<String>> resultsByConfiguration = configurations.get().stream()
                .collect(Collectors.toMap(Function.identity(), configurationName -> {
                    ClassUniquenessAnalyzer analyzer = new ClassUniquenessAnalyzer(getProject().getLogger());
                    Configuration configuration = getProject().getConfigurations().getByName(configurationName);
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
            // this is desirable because if means if people apply the plugin to lots of projects which are already
            // compliant, they don't get loads of noisy lockfiles created.
            ensureLockfileDoesNotExist();
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("# Run ./gradlew checkClassUniquenessLock --write-locks to update this file\n\n");
            // TODO(dfox): make configuration order stable!
            resultsByConfiguration.forEach((configuration, contents) -> {
                if (contents.isPresent()) {
                    stringBuilder.append("## ").append(configuration).append("\n");
                    stringBuilder.append(contents.get());
                }
            });
            ensureLockfileContains(stringBuilder.toString());
        }
    }

    private void ensureLockfileContains(String expected) {
        if (getProject().getGradle().getStartParameter().isWriteDependencyLocks()) {
            GFileUtils.writeFile(expected, lockFile);
            getLogger().lifecycle("Updated {}", getProject().getRootDir().toPath().relativize(lockFile.toPath()));
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
        if (lockFile.exists()) {
            if (getProject().getGradle().getStartParameter().isWriteDependencyLocks()) {
                GFileUtils.deleteQuietly(lockFile);
                getLogger().lifecycle("Deleted {}", getProject().getRootDir().toPath().relativize(lockFile.toPath()));
            } else {
                throw new GradleException(lockFile + " should not exist (as no problems were found).");
            }
        }
    }
}
