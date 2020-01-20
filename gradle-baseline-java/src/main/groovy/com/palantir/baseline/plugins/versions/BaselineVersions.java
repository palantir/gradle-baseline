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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import netflix.nebula.dependency.recommender.DependencyRecommendationsPlugin;
import netflix.nebula.dependency.recommender.RecommendationStrategies;
import netflix.nebula.dependency.recommender.provider.FuzzyVersionResolver;
import netflix.nebula.dependency.recommender.provider.RecommendationProviderContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskProvider;

/**
 * Transitively applies nebula.dependency recommender to replace the following common gradle snippet.
 *
 * <pre>
 * buildscript {
 *     dependencies {
 *         classpath 'com.netflix.nebula:nebula-dependency-recommender:5.2.0'
 *     }
 * }
 *
 * allprojects {
 *     apply plugin: 'nebula.dependency-recommender'
 *
 *     dependencyRecommendations {
 *         strategy OverrideTransitives
 *         propertiesFile file: project.rootProject.file('versions.props')
 *         if (file('versions.props').exists()) {
 *             propertiesFile file: project.file('versions.props')
 *         }
 *     }
 * }
 * </pre>
 */
public final class BaselineVersions implements Plugin<Project> {

    private static final Logger log = Logging.getLogger(BaselineVersions.class);
    static final String GROUP = "com.palantir.baseline-versions";
    /**
     * System property which, when true, instructs {@code nebula.dependency-recommender} to only support sourcing
     * constraints from a BOM. In that case, nebula doesn't support sourcing recommendations from {@code versions.props}
     * anymore.
     */
    public static final boolean IS_CORE_BOM_ENABLED = Boolean.getBoolean("nebula.features.coreBomSupport");

    public static final String DISABLE_PROPERTY = "com.palantir.baseline-versions.disable";

    @Override
    public void apply(Project project) {
        if (project.hasProperty(DISABLE_PROPERTY)) {
            log.info("Not configuring com.palantir.baseline-versions because " + DISABLE_PROPERTY + " was set");
            return;
        }
        // apply plugin: "nebula.dependency-recommender"
        project.getPluginManager().apply(DependencyRecommendationsPlugin.class);

        if (IS_CORE_BOM_ENABLED) {
            log.info("Not configuring nebula.dependency-recommender because coreBomSupport is enabled");
            return;
        }

        // get dependencyRecommendations extension
        RecommendationProviderContainer extension =
                project.getExtensions().getByType(RecommendationProviderContainer.class);

        extension.setStrategy(RecommendationStrategies.OverrideTransitives); // default is 'ConflictResolved'

        File rootVersionsPropsFile = rootVersionsPropsFile(project);
        extension.propertiesFile(ImmutableMap.of("file", rootVersionsPropsFile));

        if (project != project.getRootProject()) {
            // allow nested projects to specify their own nested versions.props file
            if (project.file("versions.props").exists()) {
                extension.propertiesFile(ImmutableMap.of("file", project.file("versions.props")));
            }
        } else {
            TaskProvider<CheckBomConflictTask> checkBomConflict = project.getTasks()
                    .register("checkBomConflict", CheckBomConflictTask.class, task ->
                            task.setPropsFile(rootVersionsPropsFile));
            TaskProvider<CheckNoUnusedPinTask> checkNoUnusedPin = project.getTasks()
                    .register("checkNoUnusedPin", CheckNoUnusedPinTask.class, task ->
                            task.setPropsFile(rootVersionsPropsFile));

            project.getTasks().register("checkVersionsProps", CheckVersionsPropsTask.class, task -> {
                task.dependsOn(checkBomConflict, checkNoUnusedPin);
                // If we just run checkVersionsProps --fix, we want to propagate its option to its dependent tasks
                checkBomConflict.get().setShouldFix(task.getShouldFix());
                checkNoUnusedPin.get().setShouldFix(task.getShouldFix());
            });
            // If we run with --parallel --fix, both checkNoUnusedPin and checkBomConflict will try to overwrite the
            // versions file at the same time. Therefore, make sure checkBomConflict runs first.
            checkNoUnusedPin.configure(task -> task.mustRunAfter(checkBomConflict));

            project.getPluginManager().apply(BasePlugin.class);
            project.getTasks().named("check").configure(task -> task.dependsOn("checkVersionsProps"));
        }
    }

    private static File rootVersionsPropsFile(Project project) {
        File file = project.getRootProject().file("versions.props");
        if (!file.canRead()) {
            try {
                log.info("Could not find 'versions.props' file, creating...");
                Files.createFile(file.toPath());
            } catch (IOException e) {
                log.warn("Unable to create empty versions.props file, please create this manually", e);
            }
        }
        return file;
    }

    static Set<String> getAllProjectsResolvedModuleIdentifiers(Project project) {
        return project.getRootProject().getAllprojects().stream()
                .flatMap(project2 -> getResolvedModuleIdentifiers(project2).stream())
                .collect(Collectors.toSet());
    }

    static Set<String> getResolvedModuleIdentifiers(Project project) {
        return project.getConfigurations().stream()
                .filter(Configuration::isCanBeResolved)
                .flatMap(configuration -> {
                    try {
                        ResolutionResult resolutionResult = configuration
                                .getIncoming()
                                .getResolutionResult();
                        return resolutionResult.getAllComponents().stream()
                                .map(result -> result.getId())
                                .filter(cid ->
                                        !cid.equals(resolutionResult.getRoot().getId())) // remove the project
                                .filter(cid -> cid instanceof ModuleComponentIdentifier)
                                .map(mcid -> ((ModuleComponentIdentifier) mcid).getModuleIdentifier())
                                .map(mid -> mid.getGroup() + ":" + mid.getName());
                    } catch (Exception e) {
                        throw new RuntimeException(
                                String.format(
                                        "Error during resolution of the dependency graph of " + "configuration %s",
                                        configuration),
                                e);
                    }
                })
                .collect(Collectors.toSet());
    }

    /**
     * Compares {@code versions.props} matchers by weight. Higher weight means the matcher is more specific. For
     * example,
     *
     * <pre>
     *     com.google.guava:guava
     * </pre>
     *
     * is more specific than
     *
     * <pre>
     *     com.google.guava:*
     * </pre>
     */
    static final Comparator<String> VERSIONS_PROPS_ENTRY_SPECIFIC_COMPARATOR =
            Comparator.comparing(BaselineVersions::versionsPropsMatcherWeight);

    /**
     * The weight of a matcher in {@code versions.props} according to the disambiguation logic defined in
     * {@code nebula.dependency-recommender}.
     *
     * <p>This matches the logic in {@link FuzzyVersionResolver}.
     */
    private static int versionsPropsMatcherWeight(String matcher) {
        return CharMatcher.isNot('*').countIn(matcher);
    }
}
