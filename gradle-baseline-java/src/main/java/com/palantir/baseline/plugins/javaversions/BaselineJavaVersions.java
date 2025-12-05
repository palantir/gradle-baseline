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

import com.google.common.collect.ImmutableSet;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaselineJavaVersions implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(BaselineJavaVersions.class);
    public static final String EXTENSION_NAME = "javaVersions";

    public static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("7.0");

    private static final ImmutableSet<String> LIBRARY_PLUGINS =
            ImmutableSet.of("com.palantir.external-publish-jar", "com.palantir.publish-jar");

    private static final ImmutableSet<String> DISTRIBUTION_PLUGINS = ImmutableSet.of(
            "com.palantir.external-publish-dist",
            "com.palantir.publish-dist",
            "com.palantir.sls-java-service-distribution");

    @SuppressWarnings("for-rollout:NonAbstractGradleType")
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
        @SuppressWarnings("for-rollout:GradleTypesAsFields")
        BaselineJavaVersionsExtension rootExtension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionsExtension.class, project);
        project.subprojects(proj ->
                proj.getExtensions().create(EXTENSION_NAME, SubprojectBaselineJavaVersionsExtension.class, proj));

        project.allprojects(proj -> proj.getPluginManager().withPlugin("java", unused -> {
            configureJavaProject(proj, rootExtension);
        }));
    }

    private static void configureJavaProject(Project project, BaselineJavaVersionsExtension rootExtension) {
        project.getPluginManager().apply(BaselineJavaVersion.class);
        BaselineJavaVersionExtension projectVersions =
                project.getExtensions().getByType(BaselineJavaVersionExtension.class);

        projectVersions.javaCompiler().convention(rootExtension.javaCompiler());

        Provider<ChosenJavaVersion> suggestedTarget = project.provider(() -> {
            IsLibraryWithReason isLibraryWithReason = isLibrary(project, projectVersions);
            log.info("{} is {}", project.getDisplayName(), isLibraryWithReason);
            return isLibraryWithReason.isLibrary()
                    ? ChosenJavaVersion.of(rootExtension.libraryTarget().get())
                    : rootExtension.distributionTarget().get();
        });

        Property<ChosenJavaVersion> suggestedRuntime = rootExtension.runtime();

        projectVersions.target().convention(suggestedTarget);
        projectVersions.runtime().convention(suggestedRuntime);

        project.getTasks().register("explainJavaVersions", ExplainJavaVersions.class, explainJavaVersions -> {
            explainJavaVersions.getTarget().set(projectVersions.target());
            explainJavaVersions.getDefaultTarget().set(suggestedTarget);
            explainJavaVersions.getRuntime().set(projectVersions.runtime());
            explainJavaVersions.getDefaultRuntime().set(suggestedRuntime);
            explainJavaVersions.getReasoning().set(project.provider(() -> isLibrary(project, projectVersions)
                    .toString()));
        });
    }

    private static IsLibraryWithReason isLibrary(Project project, BaselineJavaVersionExtension projectVersions) {
        Property<Boolean> libraryOverride = projectVersions.overrideLibraryAutoDetection();
        if (libraryOverride.isPresent()) {
            return new IsLibraryWithReason(
                    libraryOverride.get(), "has been overridden with `library = " + libraryOverride.get() + "`");
        }

        for (String plugin : LIBRARY_PLUGINS) {
            if (project.getPluginManager().hasPlugin(plugin)) {
                return new IsLibraryWithReason(true, String.format("has the '%s' plugin applied", plugin));
            }
        }

        for (String plugin : DISTRIBUTION_PLUGINS) {
            if (project.getPluginManager().hasPlugin(plugin)) {
                return new IsLibraryWithReason(false, String.format("has the '%s' plugin applied", plugin));
            }
        }

        PublishingExtension publishing = project.getExtensions().findByType(PublishingExtension.class);
        if (publishing != null) {
            return new IsLibraryWithReason(
                    true,
                    String.format(
                            "has publishing extensions with publications %s",
                            publishing.getPublications().getNames()));
        }

        return new IsLibraryWithReason(
                false,
                String.join(
                        "\n",
                        "didn't match any other conditions that would indicate it was a library:",
                        "  * It did not have a library plugin: " + LIBRARY_PLUGINS,
                        "  * It did not have any publishing extensions"));
    }

    private record IsLibraryWithReason(boolean isLibrary, String reason) {
        @Override
        public String toString() {
            return String.format("considered a %s because it %s", isLibrary ? "library" : "distribution", reason);
        }
    }
}
