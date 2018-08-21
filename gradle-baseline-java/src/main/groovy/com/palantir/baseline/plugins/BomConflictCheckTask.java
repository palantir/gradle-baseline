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

package com.palantir.baseline.plugins;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import netflix.nebula.dependency.recommender.provider.RecommendationProviderContainer;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;


public class BomConflictCheckTask extends DefaultTask {

    @TaskAction
    public final void checkBomConflict() {
        final Map<String, String> recommendations = getProject().getExtensions()
                .getByType(RecommendationProviderContainer.class)
                .getMavenBomProvider()
                .getRecommendations();
        List<Conflict> conflicts = new LinkedList<>();
        Set<String> artifacts = BaselineVersions.getResolvedArtifacts(getProject());
        Map<String, String> resolvedConflicts = new HashMap<>();
        BaselineVersions.checkVersionsProp(getProject(),
                pair -> {
                    String propName = pair.getLeft();
                    String propVersion = pair.getRight();
                    String regex = propName.replaceAll("\\*", ".*");
                    artifacts.forEach(artifactName -> {
                        if (artifactName.matches(regex)) {
                            resolvedConflicts.put(artifactName, propName);
                        }
                    });
                    recommendations.forEach((bomName, bomVersion) -> {
                        if (bomName.matches(regex)) {
                            conflicts.add(new Conflict(propName, propVersion, bomName, bomVersion));
                            resolvedConflicts.remove(bomName);
                        }
                    });
                    return null;
                });

        //Critical conflicts are versions.props line that only override bom recommendations with same version
        //so it should avoid considering the case where a wildcard also pin an artifact not present in the
        List<Conflict> critical = conflicts.stream()
                .filter(c -> c.getBomVersion().equals(c.getPropVersion()))
                .filter(c -> !resolvedConflicts.containsValue(c.getPropName()))
                .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            System.out.println("There are conflicts between versions.props and the bom:");
            System.out.println(conflictsToString(conflicts, resolvedConflicts));

            if (!critical.isEmpty()) {
                throw new RuntimeException("Critical conflicts between versions.props and the bom "
                        + "(overriding with same version)\n" + conflictsToString(critical, resolvedConflicts));
            }
        }

    }

    private String conflictsToString(List<Conflict> conflicts, Map<String, String> resolvedConflicts) {
        return conflicts.stream()
                .map(conflict -> conflict.details(resolvedConflicts))
                .collect(Collectors.joining("\n"));
    }

    private static class Conflict {
        private String propName;
        private String propVersion;
        private String bomName;
        private String bomVersion;

        Conflict(String propName, String propVersion, String bomName, String bomVersion) {
            this.propName = propName;
            this.propVersion = propVersion;
            this.bomName = bomName;
            this.bomVersion = bomVersion;
        }

        public String getPropName() {
            return propName;
        }

        public String getPropVersion() {
            return propVersion;
        }

        public String getBomName() {
            return bomName;
        }

        public String getBomVersion() {
            return bomVersion;
        }

        public String criticalString(Map<String, String> resolvedConflicts) {
            if (!getBomVersion().equals(getPropVersion())) {
                return "non critical: prop version not equals to bom version. (remove if unnecessary override)";
            } else if (resolvedConflicts.containsValue(getPropName())) {
                return "non critical: pin required by other non recommended artifacts: ["
                        + resolvedConflicts.entrySet().stream()
                        .filter(e -> e.getValue().equals(getPropName()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.joining(", ")) + "]";

            } else {
                return "critical: prop version equals to bom version. [redundant]";
            }
        }

        public String details(Map<String, String> resolvedConflicts) {
            return "bom:            " + getBomName() + " -> " + getBomVersion() + "\n"
                    + "versions.props: " + getPropName() + " -> " + getPropVersion() + "\n"
                    + criticalString(resolvedConflicts) + "\n";
        }
    }
}

