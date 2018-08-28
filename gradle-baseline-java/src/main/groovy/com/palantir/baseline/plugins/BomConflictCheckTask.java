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

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import netflix.nebula.dependency.recommender.provider.RecommendationProviderContainer;
import org.apache.commons.lang3.tuple.Pair;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;


public class BomConflictCheckTask extends DefaultTask {

    private final File propsFile;

    @Inject
    public BomConflictCheckTask(File propsFile) {
        this.propsFile = propsFile;
    }

    @Input
    public final Map<String, String> getMavenBomRecommendations() {
        return getProject().getExtensions()
                .getByType(RecommendationProviderContainer.class)
                .getMavenBomProvider()
                .getRecommendations();
    }

    @InputFile
    public final File getPropsFile() {
        return propsFile;
    }

    @TaskAction
    public final void checkBomConflict() {
        Map<String, String> recommendations = getMavenBomRecommendations();
        List<Conflict> conflicts = new LinkedList<>();
        Set<String> artifacts = BaselineVersions.getResolvedArtifacts(getProject());
        Map<String, String> resolvedConflicts = BaselineVersions.readVersionsProps(getPropsFile())
                .stream()
                .flatMap(pair -> {
                    String propName = pair.getLeft();
                    String propVersion = pair.getRight();
                    String regex = propName.replaceAll("\\*", ".*");

                    Set<String> recommendationConflicts = recommendations
                            .entrySet()
                            .stream()
                            .filter(entry -> entry.getKey().matches(regex))
                            .map(entry -> {
                                conflicts.add(new Conflict(propName, propVersion, entry.getKey(), entry.getValue()));
                                return entry.getKey();
                            })
                            .collect(Collectors.toSet());

                    return artifacts.stream()
                            .filter(artifactName -> artifactName.matches(regex))
                            .filter(artifactName -> !recommendationConflicts.contains(artifactName))
                            .map(artifactName -> Pair.of(artifactName, propName));
                })
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        //Critical conflicts are versions.props line that only override bom recommendations with same version
        //so it should avoid considering the case where a wildcard also pin an artifact not present in the bom
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

