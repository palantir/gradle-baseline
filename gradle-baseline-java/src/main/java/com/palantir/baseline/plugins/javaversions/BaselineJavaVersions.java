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
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BaselineJavaVersions implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(BaselineJavaVersions.class);
    public static final String EXTENSION_NAME = "javaVersions";

    public static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("7.0");

    private static final ImmutableSet<String> LIBRARY_PLUGINS =
            ImmutableSet.of("com.palantir.external-publish-jar", "com.palantir.publish-jar");

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
        project.subprojects(proj ->
                proj.getExtensions().create(EXTENSION_NAME, SubprojectBaselineJavaVersionsExtension.class, proj));

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
            log.info(
                    "{} is considered a library because it has been overridden with library = true",
                    project.getDisplayName());
            return libraryOverride.get();
        }

        for (String plugin : LIBRARY_PLUGINS) {
            if (project.getPluginManager().hasPlugin(plugin)) {
                log.info(
                        "{} is considered a library because the '{}' plugin is applied",
                        project.getDisplayName(),
                        plugin);
                return true;
            }
        }

        // The whole reason for having libraryTarget vs distributionTarget is so that we can allow distributions
        // (ie Java code that isn't published and only used in that service) to use newer source features and
        // class files, allowing everyone to support the newer compilation level *before* libraries they depend on
        // start requiring the newer compilation level. We want to upgrade distributionTarget first, wait for every
        // repo to support the new compilation level, then allow libraries to be published with the new level.
        //
        // So the assumptions here are that (at least for Palantir code):
        //   1. Only individual published jars matter for spreading higher compilation level requirements between repos
        //      when we upgrade Java versions:
        //        a) Jars inside dists/containers are not relevant as they will normally have JDK packaged with them.
        //        b) Shaded/fat jars will still need to be published to have any kind of effect.
        //   2. The *only* way to publish an individual jar is to use one of the above library plugins.
        //
        // So if the project does not use one of these jar publish plugins, we should let it use distributionTarget
        // source features.
        log.warn("{} is considered distribution code as it does not publish a jar", project.getDisplayName());
        return false;
    }
}
