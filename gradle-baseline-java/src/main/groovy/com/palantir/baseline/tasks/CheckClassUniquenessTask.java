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
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@SuppressWarnings("checkstyle:designforextension") // making this 'final' breaks gradle
public class CheckClassUniquenessTask extends DefaultTask {

    private Configuration configuration;

    public CheckClassUniquenessTask() {
        setGroup("Verification");
        setDescription("Checks that the given configuration contains no identically named classes.");
    }

    @Input
    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @TaskAction
    public void checkForDuplicateClasses() {
        ClassUniquenessAnalyzer analyzer = new ClassUniquenessAnalyzer(getLogger());
        analyzer.analyzeConfiguration(getConfiguration());
        boolean success = analyzer.getProblemJars().isEmpty();
        writeResultFile(success);

        if (!success) {
            analyzer.getProblemJars().forEach((problemJars) -> {
                Set<String> classes = analyzer.getDuplicateClassesInProblemJars(problemJars);
                getLogger().error("Identically named classes found in {} jars ({}): {}",
                        problemJars.size(), problemJars, classes);
            });

            throw new IllegalStateException(String.format(
                    "'%s' contains multiple copies of identically named classes - "
                            + "this may cause different runtime behaviour depending on classpath ordering.\n"
                            + "To resolve this, try excluding one of the following jars, "
                            + "changing a version or shadowing:\n\n%s",
                    configuration.getName(),
                    formatSummary(analyzer)
            ));
        }
    }

    private static String formatSummary(ClassUniquenessAnalyzer summary) {
        int maxLength = summary.jarsToClasses().keySet().stream().flatMap(Set::stream)
                .map(ModuleVersionIdentifier::toString)
                .map(String::length)
                .max(Comparator.naturalOrder()).get();
        String format = "%-" + (maxLength + 1) + "s";

        Map<String, Integer> sortedTable = summary.jarsToClasses().entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().stream().map(jar -> String.format(format, jar)).collect(Collectors.joining()),
                entry -> entry.getValue().size(),
                (first, second) -> {
                    throw new RuntimeException("Unexpected collision: " + first + ", " + second);
                },
                TreeMap::new));

        StringBuilder builder = new StringBuilder();
        sortedTable.forEach((jars, classes) ->
                builder.append(String.format("\t%-14s", "(" + classes + " classes) ") + jars + "\n"));
        return builder.toString();
    }

    /**
     * This only exists to convince gradle this task is incremental.
     */
    @OutputFile
    public File getResultFile() {
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
