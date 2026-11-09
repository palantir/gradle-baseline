/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.baseline.classuniqueness;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/** How the lock file shows a jar: {@code group:name}, followed by {@code  (classifier=...)} if it has one. */
final class JarLabels {
    private static final String SNAPSHOT = "SNAPSHOT";

    private JarLabels() {}

    static String label(ModuleVersionIdentifier coordinates, String jarFileName) {
        String label = coordinates.getGroup() + ":" + coordinates.getName();
        return classifier(coordinates.getName(), coordinates.getVersion(), jarFileName)
                .map(classifier -> label + " (classifier=" + classifier + ")")
                .orElse(label);
    }

    /**
     * The classifier in a jar's file name, which is the only place the public API exposes it.
     *
     * <p>Gradle names artifacts from Maven and Ivy repositories {@code <module>-<version>[-<classifier>].<extension>},
     * and unique snapshots have a timestamp in place of {@code SNAPSHOT}. Files listed in Gradle Module Metadata can
     * have any name, and Gradle takes their classifier from what follows the last occurrence of the version, so that
     * is the fallback.
     */
    static Optional<String> classifier(String module, String version, String fileName) {
        Set<String> versions = versionsIn(version, fileName);
        for (String candidate : versions) {
            String prefix = module + "-" + candidate;
            if (fileName.startsWith(prefix) && versionEndsAt(fileName, prefix.length())) {
                return classifierAfter(fileName, prefix.length());
            }
        }
        for (String candidate : versions) {
            int start = fileName.lastIndexOf("-" + candidate);
            int end = start + candidate.length() + 1;
            if (start >= 0 && versionEndsAt(fileName, end)) {
                return classifierAfter(fileName, end);
            }
        }
        return Optional.empty();
    }

    /** The coordinates of every component in a resolved graph, projects included. */
    static Map<ComponentIdentifier, ModuleVersionIdentifier> coordinatesByComponent(ResolvedComponentResult root) {
        Map<ComponentIdentifier, ModuleVersionIdentifier> coordinates = new HashMap<>();
        Set<ComponentIdentifier> visited = new HashSet<>();
        Deque<ResolvedComponentResult> toVisit = new ArrayDeque<>();
        toVisit.add(root);
        while (!toVisit.isEmpty()) {
            ResolvedComponentResult component = toVisit.pop();
            if (!visited.add(component.getId())) {
                continue;
            }
            if (component.getModuleVersion() != null) {
                coordinates.put(component.getId(), component.getModuleVersion());
            }
            for (DependencyResult dependency : component.getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult resolved) {
                    toVisit.add(resolved.getSelected());
                }
            }
        }
        return coordinates;
    }

    /** The version, and for a snapshot, the timestamped version the file name has in its place. */
    private static Set<String> versionsIn(String version, String fileName) {
        Set<String> versions = new LinkedHashSet<>();
        versions.add(version);
        if (version.endsWith("-" + SNAPSHOT)) {
            // e.g. version 1.0-SNAPSHOT in bar-1.0-20250101.123456-1-tests.jar
            String base = version.substring(0, version.length() - SNAPSHOT.length());
            Matcher timestamped = Pattern.compile(Pattern.quote(base) + "\\d{8}\\.\\d{6}-\\d+")
                    .matcher(fileName);
            if (timestamped.find()) {
                versions.add(timestamped.group());
            }
        }
        return versions;
    }

    /** Whether the version ends at {@code end}: followed by nothing, a classifier, or the extension. */
    private static boolean versionEndsAt(String fileName, int end) {
        return end == fileName.length() || fileName.charAt(end) == '-' || fileName.lastIndexOf('.') == end;
    }

    private static Optional<String> classifierAfter(String fileName, int end) {
        if (end == fileName.length() || fileName.charAt(end) != '-') {
            return Optional.empty();
        }
        String tail = fileName.substring(end + 1);
        int extension = tail.lastIndexOf('.');
        String classifier = extension >= 0 ? tail.substring(0, extension) : tail;
        return classifier.isEmpty() ? Optional.empty() : Optional.of(classifier);
    }
}
