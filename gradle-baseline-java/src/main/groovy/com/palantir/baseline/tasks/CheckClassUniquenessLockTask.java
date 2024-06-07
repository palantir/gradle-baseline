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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.palantir.baseline.services.ClassUniquenessArtifactIdentifier;
import com.palantir.baseline.services.JarClassHasher;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import difflib.DiffUtils;
import difflib.Patch;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.util.GFileUtils;

@CacheableTask
public class CheckClassUniquenessLockTask extends DefaultTask {

    private static final String HEADER = "# Danger! Multiple jars contain identically named classes. This may "
            + "cause different behaviour depending on classpath ordering.\n"
            + "# Run ./gradlew checkClassUniqueness --fix to update this file\n\n";

    // not marking this as an Input, because we want to re-run if the *contents* of a configuration changes
    @SuppressWarnings("VisibilityModifier")
    public final SetProperty<Configuration> configurations;

    @SuppressWarnings("VisibilityModifier")
    public final Property<JarClassHasher> jarClassHasher;

    @SuppressWarnings("VisibilityModifier")
    public final Property<Boolean> shouldFix;

    private final File lockFile;

    public CheckClassUniquenessLockTask() {
        this.configurations = getProject().getObjects().setProperty(Configuration.class);
        this.jarClassHasher = getProject().getObjects().property(JarClassHasher.class);
        this.shouldFix = getProject().getObjects().property(Boolean.class);
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

    @Option(option = "fix", description = "Whether to apply the suggested fix to baseline-class-uniqueness.lock")
    public final void setShouldFix(boolean shouldFix) {
        this.shouldFix.set(shouldFix);
    }

    @TaskAction
    public final void doIt() {
        ImmutableSortedMap<String, Optional<String>> resultsByConfiguration = configurations.get().stream()
                .collect(ImmutableSortedMap.toImmutableSortedMap(
                        Comparator.naturalOrder(), Configuration::getName, configuration -> {
                            ClassUniquenessAnalyzer analyzer = new ClassUniquenessAnalyzer(
                                    jarClassHasher.get(), getProject().getLogger());
                            analyzer.analyzeConfiguration(configuration);
                            Collection<Set<ClassUniquenessArtifactIdentifier>> problemJars =
                                    analyzer.getDifferingProblemJars();

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
                stringBuilder.append(contents).append('\n');
            }));
            ensureLockfileContains(stringBuilder.toString());
        }
    }

    private String clashingClasses(
            ClassUniquenessAnalyzer analyzer, Set<ClassUniquenessArtifactIdentifier> clashingJars) {
        return analyzer.getDifferingSharedClassesInProblemJars(clashingJars).stream()
                .sorted()
                .map(className -> String.format("  - %s", className))
                .collect(Collectors.joining("\n"));
    }

    private String clashingJarHeader(Set<ClassUniquenessArtifactIdentifier> clashingJars) {
        return clashingJars.stream()
                .map(ident -> {
                    String mvi = ident.moduleVersionIdentifier().getGroup() + ":"
                            + ident.moduleVersionIdentifier().getName();
                    return ident.classifier().isEmpty()
                            ? mvi
                            : mvi + " (classifier=" + ident.classifier().get() + ")";
                })
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private void ensureLockfileContains(String expected) {
        if (shouldFix.get()) {
            GFileUtils.writeFile(expected, lockFile);
            getLogger()
                    .lifecycle("Updated {}", getProject().getRootDir().toPath().relativize(lockFile.toPath()));
            return;
        }

        if (!lockFile.exists()) {
            throw new ExceptionWithSuggestion(
                    "baseline-class-uniqueness detected multiple jars containing identically named classes."
                            + " Please resolve these problems, or run `./gradlew checkClassUniqueness --fix`"
                            + "to accept them:\n\n " + expected,
                    "./gradlew checkClassUniqueness --fix");
        }

        String onDisk = GFileUtils.readFile(lockFile);
        if (!onDisk.equals(expected)) {
            List<String> onDiskLines = Splitter.on('\n').splitToList(onDisk);
            Patch<String> diff = DiffUtils.diff(onDiskLines, Splitter.on('\n').splitToList(expected));

            throw new ExceptionWithSuggestion(
                    String.join(
                            "\n",
                            String.format(
                                    "%s is out of date, please run `./gradlew checkClassUniqueness --fix` to"
                                            + " update this file. The diff is:",
                                    lockFile),
                            "",
                            String.join(
                                    "\n",
                                    DiffUtils.generateUnifiedDiff(
                                            "on disk", "expected", onDiskLines, diff, Integer.MAX_VALUE)),
                            "",
                            "On disk was:",
                            "",
                            onDisk,
                            "",
                            "Expected was:",
                            expected),
                    "./gradlew checkClassUniqueness --fix");
        }
    }

    private void ensureLockfileDoesNotExist() {
        if (lockFile.exists()) {
            if (shouldFix.get()) {
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
