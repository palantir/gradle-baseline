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
import java.util.List;
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

/** Works out a jar's {@link JarLabel} from its coordinates and file name. */
final class JarLabels {
    private static final String SNAPSHOT = "SNAPSHOT";

    /** What a unique snapshot has in place of {@code SNAPSHOT}, such as {@code 20250101.123456-1}. */
    private static final String UNIQUE_SNAPSHOT_TIMESTAMP = "\\d{8}\\.\\d{6}-\\d+";

    private JarLabels() {}

    static JarLabel label(ModuleVersionIdentifier coordinates, String jarFileName) {
        String label = coordinates.getGroup() + ":" + coordinates.getName();
        return new JarLabel(classifier(coordinates.getName(), coordinates.getVersion(), jarFileName)
                .map(classifier -> label + " (classifier=" + classifier + ")")
                .orElse(label));
    }

    /**
     * The classifier in a jar's file name, which is the only place the public API exposes it.
     *
     * <p>Gradle names artifacts from Maven and Ivy repositories {@code <module>-<version>[-<classifier>].<extension>},
     * and unique snapshots have a timestamp in place of {@code SNAPSHOT}. For {@code g:bar:1.0-SNAPSHOT}:
     * <ul>
     *   <li>{@code bar-1.0-20250101.123456-1.jar} has no classifier
     *   <li>{@code bar-1.0-20250101.123456-1-tests.jar} has classifier {@code tests}
     *   <li>{@code bar-1.0-SNAPSHOT-tests.jar}, a snapshot published without a timestamp, has classifier {@code tests}
     * </ul>
     *
     * <p>Files listed in Gradle Module Metadata can have any name, and Gradle takes their classifier from what follows
     * the last occurrence of the version, so that is the fallback.
     */
    static Optional<String> classifier(String module, String version, String fileName) {
        List<String> versions = versionsIn(version, fileName);
        // Maven and Ivy files, which start with the module and version: foo-1.0-spark-1.0.jar has classifier spark-1.0
        for (String candidate : versions) {
            String prefix = module + "-" + candidate;
            if (fileName.startsWith(prefix) && versionEndsAt(fileName, prefix.length())) {
                return classifierAfter(fileName, prefix.length());
            }
        }
        // Files named by Gradle Module Metadata: foo-core-1.0-natives.jar has classifier natives
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
    private static List<String> versionsIn(String version, String fileName) {
        if (version.endsWith("-" + SNAPSHOT)) {
            // e.g. version 1.0-SNAPSHOT in bar-1.0-20250101.123456-1-tests.jar
            String base = version.substring(0, version.length() - SNAPSHOT.length());
            Matcher timestamped =
                    Pattern.compile(Pattern.quote(base) + UNIQUE_SNAPSHOT_TIMESTAMP).matcher(fileName);
            if (timestamped.find()) {
                return List.of(version, timestamped.group());
            }
        }
        return List.of(version);
    }

    /** Whether the version ends at {@code end}: followed by nothing, a classifier, or the extension. */
    private static boolean versionEndsAt(String fileName, int end) {
        return end == fileName.length() || fileName.charAt(end) == '-' || fileName.lastIndexOf('.') == end;
    }

    /**
     * The classifier in a file name whose version ends at {@code end}. With {@code end} just after {@code 1.0}:
     * <ul>
     *   <li>{@code foo-1.0-tests.jar} gives {@code tests}
     *   <li>{@code foo-1.0-linux-x86_64.v2.jar} gives {@code linux-x86_64.v2}
     *   <li>{@code foo-1.0.jar} and {@code foo-1.0} give none
     * </ul>
     */
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
