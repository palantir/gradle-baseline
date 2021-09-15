/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;

/** A Plugin that configures a project with all Baseline settings. */
public final class Baseline implements Plugin<Project> {
    public static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("6.7");

    @Override
    public void apply(Project project) {
        if (GradleVersion.current().compareTo(MIN_GRADLE_VERSION) < 0) {
            throw new GradleException(String.format(
                    "The minimum supported Gradle version is version %s but got version %s",
                    MIN_GRADLE_VERSION, GradleVersion.current()));
        }

        Project rootProject = project.getRootProject();
        if (!project.equals(rootProject)) {
            project.getLogger()
                    .warn(
                            "com.palantir.baseline should be applied to the root project only, not '{}'",
                            project.getName());
        }

        rootProject.getPluginManager().apply(BaselineConfig.class);
        rootProject.getPluginManager().apply(BaselineCircleCi.class);
        rootProject.getPluginManager().apply(BaselineJavaVersions.class);
        rootProject.allprojects(proj -> {
            proj.getPluginManager().apply(BaselineCheckstyle.class);
            proj.getPluginManager().apply(BaselineScala.class);
            proj.getPluginManager().apply(BaselineEclipse.class);
            proj.getPluginManager().apply(BaselineIdea.class);
            proj.getPluginManager().apply(BaselineErrorProne.class);
            proj.getPluginManager().apply(BaselineFormat.class);
            proj.getPluginManager().apply(BaselineEncoding.class);
            proj.getPluginManager().apply(BaselineReproducibility.class);
            proj.getPluginManager().apply(BaselineClassUniquenessPlugin.class);
            proj.getPluginManager().apply(BaselineExactDependencies.class);
            proj.getPluginManager().apply(BaselineReleaseCompatibility.class);
            proj.getPluginManager().apply(BaselineTesting.class);
            proj.getPluginManager().apply(BaselineTestHeap.class);
            proj.getPluginManager().apply(BaselineJavaCompilerDiagnostics.class);
            proj.getPluginManager().apply(BaselineJavaParameters.class);
            proj.getPluginManager().apply(BaselineImmutables.class);
        });
    }
}
