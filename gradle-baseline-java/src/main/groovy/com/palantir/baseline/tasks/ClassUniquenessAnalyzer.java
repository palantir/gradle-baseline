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

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.hash.HashCode;
import com.palantir.baseline.services.JarClassHasher;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.slf4j.Logger;

public final class ClassUniquenessAnalyzer {
    private final JarClassHasher jarHasher;
    private final SetMultimap<Set<ModuleVersionIdentifier>, String> jarsToClasses = HashMultimap.create();
    private final SetMultimap<String, HashCode> classToHashCodes = HashMultimap.create();
    private final Logger log;

    public ClassUniquenessAnalyzer(JarClassHasher jarHasher, Logger log) {
        this.jarHasher = jarHasher;
        this.log = log;
    }

    public void analyzeConfiguration(Configuration configuration) {
        Instant before = Instant.now();
        Set<ResolvedArtifact> dependencies =
                configuration.getResolvedConfiguration().getResolvedArtifacts();

        // we use these temporary maps to accumulate information as we process each jar,
        // so they may include singletons which we filter out later
        SetMultimap<String, ModuleVersionIdentifier> classToJars = HashMultimap.create();
        SetMultimap<String, HashCode> tempClassToHashCodes = HashMultimap.create();

        for (ResolvedArtifact resolvedArtifact : dependencies) {
            File file = resolvedArtifact.getFile();
            if (!file.exists()) {
                log.info("Skipping non-existent jar {}: {}", resolvedArtifact, file);
                return;
            }

            ImmutableSetMultimap<String, HashCode> hashes =
                    jarHasher.hashClasses(resolvedArtifact, log).getHashesByClassName();

            for (Map.Entry<String, HashCode> entry : hashes.entries()) {
                String className = entry.getKey();
                HashCode hashValue = entry.getValue();
                classToJars.put(className, resolvedArtifact.getModuleVersion().getId());
                tempClassToHashCodes.put(className, hashValue);
            }
        }

        // discard all the classes that only come from one jar - these are completely safe!
        classToJars.asMap().entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .forEach(entry -> jarsToClasses.put(ImmutableSet.copyOf(entry.getValue()), entry.getKey()));

        // figure out which classes have differing hashes
        tempClassToHashCodes.asMap().entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .forEach(entry -> entry.getValue().forEach(value -> classToHashCodes.put(entry.getKey(), value)));

        Instant after = Instant.now();
        log.info(
                "Checked {} classes from {} dependencies for uniqueness ({}ms)",
                classToJars.size(),
                dependencies.size(),
                Duration.between(before, after).toMillis());
    }

    /**
     * Any groups jars that all contain some identically named classes. Note: may contain non-scary duplicates - class
     * files which are 100% identical, so their clashing name doesn't have any effect.
     */
    private Collection<Set<ModuleVersionIdentifier>> getProblemJars() {
        return jarsToClasses.keySet();
    }

    /** Class names that appear in all of the given jars. */
    public Set<String> getSharedClassesInProblemJars(Set<ModuleVersionIdentifier> problemJars) {
        return jarsToClasses.get(problemJars);
    }

    /** Jars which contain identically named classes with non-identical implementations. */
    public Collection<Set<ModuleVersionIdentifier>> getDifferingProblemJars() {
        return getProblemJars().stream()
                .filter(jars -> getDifferingSharedClassesInProblemJars(jars).size() > 0)
                .collect(Collectors.toSet());
    }

    /** Class names which appear in all of the given jars and also have non-identical implementations. */
    public Set<String> getDifferingSharedClassesInProblemJars(Set<ModuleVersionIdentifier> problemJars) {
        return getSharedClassesInProblemJars(problemJars).stream()
                .filter(classToHashCodes::containsKey)
                .collect(toSet());
    }
}
