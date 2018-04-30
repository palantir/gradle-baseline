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
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
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
        Map<String, Collection<ModuleVersionIdentifier>> classToJarMap = constructClassNameToSourceJarMap();

        Map<Set<ModuleVersionIdentifier>, Collection<String>> jarsToOverlappingClasses = new HashMap<>();
        classToJarMap.forEach((className, sourceJars) -> {
            if (sourceJars.size() > 1) {
                addToMultiMap(jarsToOverlappingClasses, new HashSet<>(sourceJars), className);
            }
        });

        boolean success = jarsToOverlappingClasses.isEmpty();
        writeResultFile(success);

        if (!success) {
            jarsToOverlappingClasses.forEach((problemJars, classes) -> {
                getLogger().error("Identically named classes found in {} jars ({}): {}",
                        problemJars.size(), problemJars, classes);
            });

            throw new IllegalStateException(String.format(
                    "'%s' contains multiple copies of identically named classes - "
                            + "this may cause different runtime behaviour depending on classpath ordering.\n"
                            + "To resolve this, try excluding one of the following jars, "
                            + "changing a version or shadowing:\n\n\t%s",
                    configuration.getName(),
                    jarsToOverlappingClasses.keySet()
            ));
        }
    }

    private Map<String, Collection<ModuleVersionIdentifier>> constructClassNameToSourceJarMap() {
        Map<String, Collection<ModuleVersionIdentifier>> classToJarMap = new ConcurrentHashMap<>();

        Instant before = Instant.now();
        Set<ResolvedArtifact> dependencies = getConfiguration()
                .getResolvedConfiguration()
                .getResolvedArtifacts();

        dependencies.parallelStream().forEach(resolvedArtifact -> {

            File file = resolvedArtifact.getFile();
            if (!file.exists()) {
                getLogger().info("Skipping non-existent jar {}: {}", resolvedArtifact, file);
                return;
            }

            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();

                    if (!jarEntry.getName().endsWith(".class")) {
                        continue;
                    }

                    addToMultiMap(classToJarMap,
                            jarEntry.getName().replaceAll("/", ".").replaceAll(".class", ""),
                            resolvedArtifact.getModuleVersion().getId());
                }
            } catch (Exception e) {
                getLogger().error("Failed to read JarFile {}, skipping...", resolvedArtifact, e);
                throw new RuntimeException(e);
            }
        });
        Instant after = Instant.now();

        getLogger().info("Checked {} classes from {} dependencies for uniqueness ({}ms)",
                classToJarMap.size(), dependencies.size(), Duration.between(before, after).toMillis());

        return classToJarMap;
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

    /**
     * Implemented here so we don't pull in guava.  Note that CopyOnWriteArrayList is threadsafe
     * so we can parallelize elsewhere.
     */
    private static <K, V> void addToMultiMap(Map<K, Collection<V>> multiMap, K key, V value) {
        multiMap.compute(key, (unused, collection) -> {
            Collection<V> newCollection = collection != null ? collection : new CopyOnWriteArrayList<>();
            newCollection.add(value);
            return newCollection;
        });
    }
}
