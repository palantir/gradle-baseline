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
import com.palantir.baseline.classuniqueness.CheckedConfiguration;
import com.palantir.baseline.classuniqueness.ConflictFinder;
import com.palantir.baseline.classuniqueness.ConflictFinder.Conflict;
import com.palantir.baseline.classuniqueness.ConflictFinder.Jar;
import com.palantir.baseline.classuniqueness.JarLabel;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import difflib.DiffUtils;
import difflib.Patch;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

@CacheableTask
public abstract class CheckClassUniquenessLockTask extends DefaultTask {

    private static final String HEADER = "# Danger! Multiple jars contain identically named classes. This may "
            + "cause different behaviour depending on classpath ordering.\n"
            + "# Run ./gradlew checkClassUniqueness --fix to update this file\n\n";

    // Lets builds add configurations with `configurations.add ...`, as they did when this was a public SetProperty
    // field. Configurations can't be stored in the configuration cache, so each one is captured as a
    // CheckedConfiguration when it is added, and this set is left out of the cache.
    @SuppressWarnings("for-rollout:GradleTypesAsFields")
    private final transient DomainObjectSet<Configuration> configurations;

    public CheckClassUniquenessLockTask() {
        this.configurations = getObjects().domainObjectSet(Configuration.class);
        configurations.all(configuration -> {
            CheckedConfiguration checked = getObjects().newInstance(CheckedConfiguration.class);
            checked.from(configuration);
            getCheckedConfigurations().add(checked);
        });
        onlyIf(
                "there are configurations to check",
                _task -> !getCheckedConfigurations().get().isEmpty());
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    /** Configurations whose jars must not contain identically named classes with differing content. */
    @Internal
    public final DomainObjectSet<Configuration> getConfigurations() {
        return configurations;
    }

    @Nested
    public abstract ListProperty<CheckedConfiguration> getCheckedConfigurations();

    @Internal
    @Option(option = "fix", description = "Whether to apply the suggested fix to baseline-class-uniqueness.lock")
    public abstract Property<Boolean> getShouldFix();

    @OutputFile
    public abstract RegularFileProperty getLockFile();

    /** The lock file's path is printed relative to this directory when {@code --fix} writes or deletes it. */
    @Internal
    public abstract DirectoryProperty getLogPathsRelativeTo();

    @TaskAction
    public final void doIt() throws IOException {
        SortedMap<String, List<Conflict>> conflictsByConfigurationName = new TreeMap<>();
        for (CheckedConfiguration configuration : getCheckedConfigurations().get()) {
            Instant before = Instant.now();
            List<Jar> jars = configuration.loadJars();
            List<Conflict> conflicts = ConflictFinder.find(jars);
            getLogger()
                    .info(
                            "Checked {} classes from {} dependencies for uniqueness ({}ms)",
                            jars.stream().mapToInt(jar -> jar.index().size()).sum(),
                            jars.size(),
                            Duration.between(before, Instant.now()).toMillis());
            if (!conflicts.isEmpty()) {
                conflictsByConfigurationName.put(
                        configuration.getConfigurationName().get(), conflicts);
            }
        }

        if (conflictsByConfigurationName.isEmpty()) {
            // this is desirable because if means if people apply the plugin to lots of projects which are already
            // compliant, they don't get loads of noisy lockfiles created.
            ensureLockfileDoesNotExist();
        } else {
            ensureLockfileContains(lockFileContents(conflictsByConfigurationName));
        }
    }

    private static String lockFileContents(SortedMap<String, List<Conflict>> conflictsByConfigurationName) {
        StringBuilder contents = new StringBuilder(HEADER);
        for (Map.Entry<String, List<Conflict>> configuration : conflictsByConfigurationName.entrySet()) {
            String configurationName = configuration.getKey();
            List<Conflict> conflicts = configuration.getValue();
            contents.append("## ").append(configurationName).append('\n');
            contents.append(lockFileSection(conflicts)).append('\n');
        }
        return contents.toString();
    }

    private static String lockFileSection(List<Conflict> conflicts) {
        SortedMap<String, SortedSet<String>> classesByHeader = new TreeMap<>();
        for (Conflict conflict : conflicts) {
            classesByHeader.put(clashingJarHeader(conflict.jarLabels()), conflict.classNames());
        }
        return EntryStream.of(classesByHeader)
                .flatMapKeyValue((header, classNames) -> StreamEx.of(classNames)
                        .map(className -> String.format("  - %s", className))
                        .prepend(header))
                .joining("\n");
    }

    private static String clashingJarHeader(Set<JarLabel> jarLabels) {
        return jarLabels.stream().map(JarLabel::value).sorted().collect(Collectors.joining(", ", "[", "]"));
    }

    private void ensureLockfileContains(String expected) {
        File lockFile = getLockFile().get().getAsFile();
        if (getShouldFix().get()) {
            try {
                Files.writeString(lockFile.toPath(), expected);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            getLogger()
                    .lifecycle(
                            "Updated {}",
                            getLogPathsRelativeTo().get().getAsFile().toPath().relativize(lockFile.toPath()));
            return;
        }

        if (!lockFile.exists()) {
            throw new ExceptionWithSuggestion(
                    "baseline-class-uniqueness detected multiple jars containing identically named classes."
                            + " Please resolve these problems, or run `./gradlew checkClassUniqueness --fix`"
                            + " to accept them:\n\n " + expected,
                    "./gradlew checkClassUniqueness --fix");
        }

        String onDisk;
        try {
            onDisk = Files.readString(lockFile.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
        File lockFile = getLockFile().get().getAsFile();
        if (lockFile.exists()) {
            if (getShouldFix().get()) {
                FileUtils.deleteQuietly(lockFile);
                getLogger()
                        .lifecycle(
                                "Deleted {}",
                                getLogPathsRelativeTo()
                                        .get()
                                        .getAsFile()
                                        .toPath()
                                        .relativize(lockFile.toPath()));
            } else {
                throw new ExceptionWithSuggestion(
                        String.format(
                                "%s should not exist (as no problems were found), please run `./gradlew"
                                        + " checkClassUniqueness --fix` to remove this file.",
                                lockFile),
                        "./gradlew checkClassUniqueness --fix");
            }
        }
    }
}
