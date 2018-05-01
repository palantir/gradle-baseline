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
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.slf4j.Logger;

public final class ClassUniquenessAnalyzer {

    private final Map<String, Set<ModuleVersionIdentifier>> classToJarsMap = new HashMap<>();
    private final Map<Set<ModuleVersionIdentifier>, Set<String>> jarsToClasses = new HashMap<>();
    private final Logger log;

    public ClassUniquenessAnalyzer(Logger log) {
        this.log = log;
    }

    public void analyzeConfiguration(Configuration configuration) {
        Instant before = Instant.now();

        Set<ResolvedArtifact> dependencies = configuration
                .getResolvedConfiguration()
                .getResolvedArtifacts();

        Map<String, Set<ModuleVersionIdentifier>> tempClassToJarMap = new HashMap<>();
        dependencies.stream().forEach(resolvedArtifact -> {
            File file = resolvedArtifact.getFile();
            if (!file.exists()) {
                log.info("Skipping non-existent jar {}: {}", resolvedArtifact, file);
                return;
            }

            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();

                    if (!jarEntry.getName().endsWith(".class")) {
                        continue;
                    }

                    multiMapPut(tempClassToJarMap,
                            jarEntry.getName().replaceAll("/", ".").replaceAll(".class", ""),
                            resolvedArtifact.getModuleVersion().getId());
                }
            } catch (Exception e) {
                log.error("Failed to read JarFile {}", resolvedArtifact, e);
                throw new RuntimeException(e);
            }
        });

        tempClassToJarMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .forEach(entry -> {
                    // add to the top level map
                    entry.getValue().forEach(value -> multiMapPut(classToJarsMap, entry.getKey(), value));

                    // add to the opposite direction index
                    multiMapPut(jarsToClasses, entry.getValue(), entry.getKey());
                });
        
        Instant after = Instant.now();
        log.info("Checked {} classes from {} dependencies for uniqueness ({}ms)",
                tempClassToJarMap.size(), dependencies.size(), Duration.between(before, after).toMillis());
    }

    public Collection<Set<ModuleVersionIdentifier>> getProblemJars() {
        return classToJarsMap.values();
    }

    public Map<Set<ModuleVersionIdentifier>, Set<String>> jarsToClasses() {
        return jarsToClasses;
    }

    public Set<String> getDuplicateClassesInProblemJars(Set<ModuleVersionIdentifier> problemJars) {
        return jarsToClasses.get(problemJars);
    }

    private static <K, V> void multiMapPut(Map<K, Set<V>> map, K key, V value) {
        map.compute(key, (unused, collection) -> {
            Set<V> newCollection = collection != null ? collection : new HashSet<>();
            newCollection.add(value);
            return newCollection;
        });
    }
}
