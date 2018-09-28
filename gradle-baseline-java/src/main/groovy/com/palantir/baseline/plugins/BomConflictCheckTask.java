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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import netflix.nebula.dependency.recommender.DependencyRecommendationsPlugin;
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

    @Input
    public final Set<String> getResolvedArtifacts() {
        return BaselineVersions.getAllProjectsResolvedArtifacts(getProject());
    }

    @InputFile
    public final File getPropsFile() {
        return propsFile;
    }

    @TaskAction
    public final void checkBomConflict() {
        List<Conflict> conflicts = Lists.newArrayList();
        Set<String> artifacts = getResolvedArtifacts();
        Map<String, String> recommendations = getMavenBomRecommendations();
        Set<String> bomDeps = getProject().getConfigurations()
                .getByName(DependencyRecommendationsPlugin.NEBULA_RECOMMENDER_BOM)
                .getAllDependencies()
                .stream()
                .map(dep -> dep.getGroup() + ":" + dep.getName())
                .collect(Collectors.toSet());

        Map<String, String> resolvedConflicts = VersionsPropsReader.readVersionsProps(getPropsFile())
                .stream()
                .flatMap(pair -> {
                    String propName = pair.getLeft();
                    String propVersion = pair.getRight();
                    String regex = propName.replaceAll("\\*", ".*");

                    Set<String> recommendationConflicts = recommendations
                            .entrySet()
                            .stream()
                            // Don't report conflicts for artifacts that are used to configure bom recommendations
                            .filter(entry -> !bomDeps.contains(entry.getKey()))
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
                .collect(Collectors.toMap(
                        Pair::getLeft,
                        Pair::getRight,
                        // Resolve conflicts by choosing the longer entry because it is more specific
                        (propName1, propName2) -> {
                            if (propName1.equals(propName2)) {
                                throw new RuntimeException("Duplicate versions.props entry: " + propName1);
                            }
                            return Stream.of(propName1, propName2)
                                    .max(Comparator.comparingLong(String::length))
                                    .get();
                        }));

        Set<String> versionPropConflictingLines = ImmutableSet.copyOf(resolvedConflicts.values());

        //Critical conflicts are versions.props line that only override bom recommendations with same version
        //so it should avoid considering the case where a wildcard also pin an artifact not present in the bom
        List<Conflict> critical = conflicts.stream()
                .filter(c -> c.getBomVersion().equals(c.getPropVersion()))
                .filter(c -> !versionPropConflictingLines.contains(c.getPropName()))
                .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            getProject().getLogger().info("There are conflicts between versions.props and the bom:\n{}",
                    conflictsToString(conflicts, resolvedConflicts));

            if (!critical.isEmpty()) {
                throw new RuntimeException("Critical conflicts between versions.props and the bom "
                        + "(overriding with same version)\n" + conflictsToString(critical, resolvedConflicts));
            }
        }

    }

    private String conflictsToString(List<Conflict> conflicts, Map<String, String> resolvedConflicts) {
        return conflicts.stream()
                .collect(Collectors.groupingBy(Conflict::getPropName))
                .entrySet()
                .stream()
                .map(entry -> allConflictsWithBom(entry.getKey(), entry.getValue(), resolvedConflicts))
                .collect(Collectors.joining("\n"));
    }

    private String allConflictsWithBom(String propName, List<Conflict> conflicts,
            Map<String, String> resolvedConflicts) {
        String sameVersionExplanation = resolvedConflicts.containsValue(propName)
                ? String.format("non critical: pin required by other non recommended artifacts: %s\n",
                        resolvedConflicts.entrySet().stream()
                        .filter(e -> e.getValue().equals(propName))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.joining(", ", "[", "]")))
                : "";

        String bomDetails = conflicts.size() == 1
                ? String.format("  bom:            %s", conflicts.get(0).bomDetail())
                : String.format("  bom:\n%s", conflicts.stream()
                        .map(Conflict::bomDetail)
                        .map(str -> "                  " + str)
                        .collect(Collectors.joining("\n")));
        return String.format("%s  versions.props: %s -> %s\n%s",
                sameVersionExplanation, propName, conflicts.get(0).getPropVersion(), bomDetails);
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

        public String bomDetail() {
            return bomName + " -> " + bomVersion;
        }
    }
}

