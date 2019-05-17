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

package com.palantir.baseline.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public class CheckClassUniquenessTask extends DefaultTask {

    private Configuration configuration;

    public CheckClassUniquenessTask() {
        setGroup("Verification");
        setDescription("Checks that the given configuration contains no identically named classes.");
    }

    @InputFiles
    public final Configuration getConfiguration() {
        return configuration;
    }

    public final void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @TaskAction
    public final void checkForDuplicateClasses() {
        ClassUniquenessAnalyzer analyzer = new ClassUniquenessAnalyzer(getLogger());
        analyzer.analyzeConfiguration(getConfiguration());
        boolean success = analyzer.getDifferingProblemJars().isEmpty();
        writeResultFile(success);

        if (!success) {
            analyzer.getDifferingProblemJars().forEach(problemJars -> {
                Set<String> differingClasses = analyzer.getDifferingSharedClassesInProblemJars(problemJars);
                getLogger().error("{} Identically named classes with differing impls found in {}: {}",
                        differingClasses.size(), problemJars, differingClasses);
            });

            throw new IllegalStateException(String.format(
                    "'%s' contains multiple copies of identically named classes - "
                            + "this may cause different runtime behaviour depending on classpath ordering.\n"
                            + "To resolve this, try excluding one of the following jars:\n\n%s",
                    configuration.getName(),
                    formatSummary(analyzer)
            ));
        }
    }

    private static String formatSummary(ClassUniquenessAnalyzer summary) {
        Collection<Set<ModuleVersionIdentifier>> allProblemJars = summary.getDifferingProblemJars();

        int maxLength = allProblemJars.stream().flatMap(Set::stream)
                .map(ModuleVersionIdentifier::toString)
                .map(String::length)
                .max(Comparator.naturalOrder()).get();
        String format = "%-" + (maxLength + 1) + "s";

        StringBuilder builder = new StringBuilder();

        allProblemJars.forEach(problemJars -> {
            int count = summary.getDifferingSharedClassesInProblemJars(problemJars).size();
            String countColumn = String.format("\t%-14s", "(" + count + " classes) ");
            builder.append(countColumn);

            String jars = problemJars.stream().map(jar -> String.format(format, jar)).collect(Collectors.joining());
            builder.append(jars);

            builder.append('\n');
        });

        return builder.toString();
    }

    /**
     * This only exists to convince gradle this task is incremental.
     */
    @OutputFile
    public final File getResultFile() {
        return getProject().getBuildDir().toPath()
                .resolve(Paths.get("uniqueClassNames", configuration.getName()))
                .toFile();
    }

    private void writeResultFile(boolean success) {
        try {
            File result = getResultFile();
            Files.createDirectories(result.toPath().getParent());
            Files.write(result.toPath(), Boolean.toString(success).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write boolean result file", e);
        }
    }
}
