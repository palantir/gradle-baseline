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

package com.palantir.baseline.plugins.versions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.palantir.baseline.util.VersionsProps;
import com.palantir.baseline.util.VersionsProps.ParsedVersionsProps;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import netflix.nebula.dependency.recommender.DependencyRecommendationsPlugin;
import netflix.nebula.dependency.recommender.provider.RecommendationProviderContainer;
import org.apache.commons.lang3.tuple.Pair;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class CheckBomConflictTask extends DefaultTask {

    private final Property<Boolean> shouldFix = getProject().getObjects().property(Boolean.class);
    private final RegularFileProperty propsFileProperty = getProject()
            .getObjects()
            .fileProperty();

    public CheckBomConflictTask() {
        shouldFix.set(false);
        setGroup(BaselineVersions.GROUP);
        setDescription("Ensures your versions.props pins don't force the same version that is already recommended by a "
                + "BOM");
    }

    final void setPropsFile(File propsFile) {
        this.propsFileProperty.set(propsFile);
    }

    @Input
    public final Map<String, String> getMavenBomRecommendations() {
        return getProject()
                .getExtensions()
                .getByType(RecommendationProviderContainer.class)
                .getMavenBomProvider()
                .getRecommendations();
    }

    @Input
    public final Set<String> getResolvedArtifacts() {
        return BaselineVersions.getAllProjectsResolvedModuleIdentifiers(getProject());
    }

    @InputFile
    public final Provider<RegularFile> getPropsFile() {
        return propsFileProperty;
    }

    @Option(option = "fix", description = "Whether to apply the suggested fix to versions.props")
    public final void setShouldFix(boolean shouldFix) {
        this.shouldFix.set(shouldFix);
    }

    final void setShouldFix(Provider<Boolean> shouldFix) {
        this.shouldFix.set(shouldFix);
    }

    @TaskAction
    public final void checkBomConflict() {
        List<Conflict> conflicts = Lists.newArrayList();
        Set<String> artifacts = getResolvedArtifacts();
        Map<String, String> recommendations = getMavenBomRecommendations();
        Set<String> bomDeps = getProject()
                .getConfigurations()
                .getByName(DependencyRecommendationsPlugin.NEBULA_RECOMMENDER_BOM)
                .getAllDependencies()
                .stream()
                .map(dep -> dep.getGroup() + ":" + dep.getName())
                .collect(Collectors.toSet());

        ParsedVersionsProps parsedVersionsProps = VersionsProps.readVersionsProps(
                getPropsFile().get().getAsFile());
        // Map of (artifact name not defined from BOM) -> (version props line it 'vindicates', i.e. confirms is used)
        Map<String, String> resolvedConflicts = parsedVersionsProps.forces().stream()
                .flatMap(force -> {
                    String propName = force.name();
                    String regex = propName.replaceAll("\\*", ".*");

                    Set<String> recommendationConflicts = recommendations.entrySet().stream()
                            // Don't report conflicts for artifacts that are used to configure bom recommendations
                            .filter(entry -> !bomDeps.contains(entry.getKey()))
                            .filter(entry -> entry.getKey().matches(regex))
                            .map(entry -> {
                                conflicts.add(
                                        new Conflict(propName, force.version(), entry.getKey(), entry.getValue()));
                                return entry.getKey();
                            })
                            .collect(Collectors.toSet());

                    return artifacts.stream()
                            .filter(artifactName -> artifactName.matches(regex))
                            .filter(artifactName -> !recommendationConflicts.contains(artifactName))
                            .map(artifactName -> Pair.of(artifactName, propName));
                })
                // Resolve conflicts by choosing the more specific entry
                .collect(Collectors.toMap(
                        Pair::getLeft,
                        Pair::getRight,
                        BinaryOperator.maxBy(BaselineVersions.VERSIONS_PROPS_ENTRY_SPECIFIC_COMPARATOR)));

        Set<String> versionPropsVindicatedLines = ImmutableSet.copyOf(resolvedConflicts.values());

        // Critical conflicts are versions.props line that only override bom recommendations with same version
        // so it should avoid considering the case where a wildcard also pin an artifact not present in the bom
        List<Conflict> critical = conflicts.stream()
                .filter(c -> c.getBomVersion().equals(c.getPropVersion()))
                .filter(c -> !versionPropsVindicatedLines.contains(c.getPropName()))
                .collect(Collectors.toList());

        if (conflicts.isEmpty()) {
            return;
        }
        getProject()
                .getLogger()
                .info(
                        "There are conflicts between versions.props and the bom:\n{}",
                        conflictsToString(conflicts, resolvedConflicts));

        if (critical.isEmpty()) {
            return;
        }

        if (shouldFix.get()) {
            List<String> toRemove = critical.stream().map(Conflict::getPropName).collect(Collectors.toList());
            getProject()
                    .getLogger()
                    .lifecycle("Removing critical conflicts from versions.props:\n"
                            + toRemove.stream()
                                    .map(name -> String.format(" - '%s'", name))
                                    .collect(Collectors.joining("\n")));
            VersionsProps.writeVersionsProps(
                    parsedVersionsProps, toRemove.stream(), getPropsFile().get().getAsFile());
            return;
        }

        throw new RuntimeException("Critical conflicts between versions.props and the bom "
                + "(overriding with same version)\n"
                + conflictsToString(critical, resolvedConflicts));
    }

    private String conflictsToString(List<Conflict> conflicts, Map<String, String> resolvedConflicts) {
        return conflicts.stream().collect(Collectors.groupingBy(Conflict::getPropName)).entrySet().stream()
                .map(entry -> allConflictsWithBom(entry.getKey(), entry.getValue(), resolvedConflicts))
                .collect(Collectors.joining("\n"));
    }

    private String allConflictsWithBom(
            String propName, List<Conflict> conflicts, Map<String, String> resolvedConflicts) {
        String sameVersionExplanation = resolvedConflicts.containsValue(propName)
                ? String.format(
                        "non critical: pin required by other non recommended artifacts: %s\n",
                        resolvedConflicts.entrySet().stream()
                                .filter(e -> e.getValue().equals(propName))
                                .map(Map.Entry::getKey)
                                .collect(Collectors.joining(", ", "[", "]")))
                : "";

        String bomDetails = conflicts.size() == 1
                ? String.format("  bom:            %s", conflicts.get(0).bomDetail())
                : String.format(
                        "  bom:\n%s",
                        conflicts.stream()
                                .map(Conflict::bomDetail)
                                .map(str -> "                  " + str)
                                .collect(Collectors.joining("\n")));
        return String.format(
                "%s  versions.props: %s -> %s\n%s",
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
