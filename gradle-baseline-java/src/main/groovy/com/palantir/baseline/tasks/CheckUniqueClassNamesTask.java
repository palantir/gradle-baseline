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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

@SuppressWarnings("checkstyle:designforextension") // making this 'final' breaks gradle
public class CheckUniqueClassNamesTask extends DefaultTask {

    private Configuration configuration;

    public CheckUniqueClassNamesTask() {
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
        Set<File> files = getConfiguration().getResolvedConfiguration().getFiles();
        Map<String, Set<File>> classToJarMap = new HashMap<>();

        for (File file : files) {
            try (JarFile jarFile1 = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile1.entries();

                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();

                    if (!jarEntry.getName().endsWith(".class")) {
                        continue;
                    }

                    HashSet<File> initialSet = new HashSet<>();
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
            throw new IllegalStateException(String.format(
                    "%s contains duplicate classes: %s",
                    getConfiguration().getName(), errors.toString()));
        }
    }
}
