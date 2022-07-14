/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins.javaversions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaselineJavaVersions implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(BaselineJavaVersions.class);
    public static final String EXTENSION_NAME = "javaVersions";

    public static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("7.0");
    // 'nebula.maven-publish' and 'com.palantir.shadow-jar' create publications lazily which cause inconsistencies
    // based on ordering.
    private static final ImmutableSet<String> LIBRARY_PLUGINS =
            ImmutableSet.of("nebula.maven-publish", "com.palantir.shadow-jar");

    @Override
    public void apply(Project project) {
        if (!Objects.equals(project, project.getRootProject())) {
            throw new GradleException("BaselineJavaVersions may only be applied to the root project");
        }
        GradleVersion currentGradleVersion = GradleVersion.current();
        if (currentGradleVersion.compareTo(MIN_GRADLE_VERSION) < 0) {
            throw new GradleException(String.format(
                    "BaselineJavaVersions requires %s. %s is not supported", MIN_GRADLE_VERSION, currentGradleVersion));
        }
        BaselineJavaVersionsExtension rootExtension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionsExtension.class, project);
        project.allprojects(proj -> proj.getPluginManager().withPlugin("java", unused -> {
            proj.getPluginManager().apply(BaselineJavaVersion.class);
            BaselineJavaVersionExtension projectVersions =
                    proj.getExtensions().getByType(BaselineJavaVersionExtension.class);

            Provider<ChosenJavaVersion> suggestedTarget = proj.provider(() -> isLibrary(proj, projectVersions)
                    ? ChosenJavaVersion.of(rootExtension.libraryTarget().get())
                    : rootExtension.distributionTarget().get());

            projectVersions.target().convention(suggestedTarget);
            projectVersions.runtime().convention(rootExtension.runtime());
        }));
    }

    private static boolean isLibrary(Project project, BaselineJavaVersionExtension projectVersions) {
        Property<Boolean> libraryOverride = projectVersions.overrideLibraryAutoDetection();
        if (libraryOverride.isPresent()) {
            log.debug(
                    "Project '{}' is considered a library because it has been overridden with library = true",
                    project.getDisplayName());
            return libraryOverride.get();
        }
        for (String plugin : LIBRARY_PLUGINS) {
            if (project.getPluginManager().hasPlugin(plugin)) {
                log.debug(
                        "Project '{}' is considered a library because the '{}' plugin is applied",
                        project.getDisplayName(),
                        plugin);
                return true;
            }
        }
        PublishingExtension publishing = project.getExtensions().findByType(PublishingExtension.class);
        if (publishing == null) {
            log.debug(
                    "Project '{}' is considered a distribution, not a library, because "
                            + "it doesn't define any publishing extensions",
                    project.getDisplayName());
            return false;
        }
        ImmutableList<String> jarPublications = publishing.getPublications().stream()
                .filter(pub -> isLibraryPublication(project, pub))
                .map(Named::getName)
                .collect(ImmutableList.toImmutableList());
        if (jarPublications.isEmpty()) {
            log.debug(
                    "Project '{}' is considered a distribution because it does not publish jars",
                    project.getDisplayName());
            return false;
        }
        log.debug(
                "Project '{}' is considered a library because it publishes jars: {}",
                project.getDisplayName(),
                jarPublications);
        return true;
    }

    private static boolean isLibraryPublication(Project project, Publication publication) {
        if (publication instanceof MavenPublication) {
            MavenPublication mavenPublication = (MavenPublication) publication;
            return mavenPublication.getArtifacts().stream().anyMatch(artifact -> "jar".equals(artifact.getExtension()));
        }
        if (publication instanceof IvyPublication) {
            IvyPublication ivyPublication = (IvyPublication) publication;
            return ivyPublication.getArtifacts().stream().anyMatch(artifact -> "jar".equals(artifact.getExtension()));
        }
        // Default to true for unknown publication types to avoid setting higher jvm targets than necessary
        log.warn(
                "Unknown publication '{}' of type '{}'. Assuming project {} is a library",
                publication,
                publication.getClass().getName(),
                project.getName());
        return true;
    }
}
