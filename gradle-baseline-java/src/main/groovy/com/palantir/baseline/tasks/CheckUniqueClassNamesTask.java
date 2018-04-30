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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

@SuppressWarnings("checkstyle:designforextension") // making this 'final' breaks gradle
public class CheckUniqueClassNamesTask extends DefaultTask {

    private Configuration configuration;

    public CheckUniqueClassNamesTask() {
        setGroup("Verification");
        setDescription("Checks that the given configuration contains no identically named classes.");
    }

    @InputFiles
    @Classpath
    @CompileClasspath
    @SkipWhenEmpty
    public Iterable<File> getClasspath() {
        return configuration.getResolvedConfiguration().getFiles();
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @TaskAction
    public void checkForDuplicateClasses() {
        Map<String, Set<File>> classToJarMap = new HashMap<>();

        for (File file : getClasspath()) {
            try (JarFile jarFile1 = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile1.entries();

                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();

                    if (!jarEntry.getName().endsWith(".class")) {
                        continue;
                    }

                    Set<File> initialSet = new HashSet<>();
                    Set<File> previous = classToJarMap.putIfAbsent(jarEntry.getName(), initialSet);
                    if (previous != null) {
                        previous.add(file);
                    } else {
                        initialSet.add(file);
                    }
                }
            } catch (Exception e) {
                getLogger().error("Failed to read JarFile {}", file, e);
            }
        }

        StringBuilder errors = new StringBuilder();
        for (String className : classToJarMap.keySet()) {
            Set<File> jars = classToJarMap.get(className);
            if (jars.size() > 1) {
                errors.append(String.format(
                        "%s appears in: %s\n", className, jars));
            }
        }

        if (errors.length() > 0) {
            writeResult(false);
            throw new IllegalStateException(String.format(
                    "%s contains duplicate classes: %s",
                    configuration.getName(), errors.toString()));
        }

        writeResult(true);
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

    private void writeResult(boolean success) {
        try {
            File result = getResultFile();
            Files.createDirectories(result.toPath().getParent());
            Files.write(result.toPath(), Boolean.toString(success).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Unable to write boolean result file", e);
        }
    }
}
