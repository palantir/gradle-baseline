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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GFileUtils;

@CacheableTask
public class CheckClassUniquenessLockTask extends DefaultTask {

    private static final String HEADER = "# Danger! Multiple jars contain identically named classes. This may "
            + "cause different behaviour depending on classpath ordering.\n"
            + "# Run ./gradlew checkClassUniqueness --write-locks to update this file\n\n";

    // not marking this as an Input, because we want to re-run if the *contents* of a configuration changes
    @SuppressWarnings("VisibilityModifier")
    public final SetProperty<Configuration> configurations;

    private final File lockFile;

    public CheckClassUniquenessLockTask() {
        this.configurations = getProject().getObjects().setProperty(Configuration.class);
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
    public final Map<String, ImmutableList<String>> getContentsOfAllConfigurations() {
        return configurations.get().stream().collect(Collectors.toMap(Configuration::getName, configuration -> {
            return configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                    .map(resolvedComponentResult -> Objects.toString(resolvedComponentResult.getModuleVersion()))
                    .collect(ImmutableList.toImmutableList()); // Gradle requires this to be Serializable
        }));
    }

    @OutputFile
    public final File getLockFile() {
        return lockFile;
    }

    @TaskAction
    public final void doIt() {
        ImmutableSortedMap<String, Optional<String>> resultsByConfiguration = configurations.get().stream()
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.naturalOrder(), Configuration::getName, configuration -> {
                            ClassUniquenessAnalyzer analyzer =
                                    new ClassUniquenessAnalyzer(getProject().getLogger());
                            analyzer.analyzeConfiguration(configuration);
                            Collection<Set<ModuleVersionIdentifier>> problemJars = analyzer.getDifferingProblemJars();

                            if (problemJars.isEmpty()) {
                                return Optional.empty();
                            }

                            ImmutableSortedMap<String, String> clashingHeadersToClasses = problemJars.stream()
                                    .collect(ImmutableSortedMap.toImmutableSortedMap(
                                            Comparator.naturalOrder(),
                                            this::clashingJarHeader,
                                            clashingJars -> clashingClasses(analyzer, clashingJars)));

                            return Optional.of(clashingHeadersToClasses.entrySet().stream()
                                    .flatMap(entry -> {
                                        String clashingJarHeader = entry.getKey();
                                        String clashingClasses = entry.getValue();
                                        return Stream.of(clashingJarHeader, clashingClasses);
                                    })
                                    .collect(Collectors.joining("\n")));
                        }));

        boolean conflictsFound = resultsByConfiguration.values().stream().anyMatch(Optional::isPresent);
        if (!conflictsFound) {
            // this is desirable because if means if people apply the plugin to lots of projects which are already
            // compliant, they don't get loads of noisy lockfiles created.
            ensureLockfileDoesNotExist();
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(HEADER);
            resultsByConfiguration.forEach((configuration, maybeContents) -> maybeContents.ifPresent(contents -> {
                stringBuilder.append("## ").append(configuration).append("\n");
                stringBuilder.append(contents);
            }));
            stringBuilder.append('\n');
            ensureLockfileContains(stringBuilder.toString());
        }
    }

    private String clashingClasses(ClassUniquenessAnalyzer analyzer, Set<ModuleVersionIdentifier> clashingJars) {
        return analyzer.getDifferingSharedClassesInProblemJars(clashingJars).stream()
                .sorted()
                .map(className -> String.format("  - %s", className))
                .collect(Collectors.joining("\n"));
    }

    private String clashingJarHeader(Set<ModuleVersionIdentifier> clashingJars) {
        return clashingJars.stream()
                .map(mvi -> mvi.getGroup() + ":" + mvi.getName())
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private void ensureLockfileContains(String expected) {
        if (getProject().getGradle().getStartParameter().isWriteDependencyLocks()) {
            GFileUtils.writeFile(expected, lockFile);
            getLogger()
                    .lifecycle("Updated {}", getProject().getRootDir().toPath().relativize(lockFile.toPath()));
            return;
        }

        if (!lockFile.exists()) {
            throw new GradleException("baseline-class-uniqueness detected multiple jars containing identically named "
                    + "classes. Please resolve these problems, or run `./gradlew checkClassUniqueness "
                    + "--write-locks` to accept them:\n\n"
                    + expected);
        }

        String onDisk = GFileUtils.readFile(lockFile);
        if (!onDisk.equals(expected)) {
            throw new GradleException(lockFile
                    + " is out of date, please run `./gradlew "
                    + "checkClassUniqueness --write-locks` to update this file");
        }
    }

    private void ensureLockfileDoesNotExist() {
        if (lockFile.exists()) {
            if (getProject().getGradle().getStartParameter().isWriteDependencyLocks()) {
                GFileUtils.deleteQuietly(lockFile);
                getLogger()
                        .lifecycle(
                                "Deleted {}", getProject().getRootDir().toPath().relativize(lockFile.toPath()));
            } else {
                throw new GradleException(lockFile + " should not exist (as no problems were found).");
            }
        }
    }
}
