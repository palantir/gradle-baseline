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

import com.palantir.baseline.plugins.versions.BaselineVersions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** A Plugin that configures a project with all Baseline settings. */
public final class Baseline implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        Project rootProject = project.getRootProject();
        if (!project.equals(rootProject)) {
            project.getLogger()
                    .warn(
                            "com.palantir.baseline should be applied to the root project only, not '{}'",
                            project.getName());
        }

        rootProject.getPluginManager().apply(BaselineConfig.class);
        rootProject.getPluginManager().apply(BaselineCircleCi.class);
        rootProject.allprojects(proj -> {
            proj.getPluginManager().apply(BaselineCheckstyle.class);
            proj.getPluginManager().apply(BaselineScalastyle.class);
            proj.getPluginManager().apply(BaselineEclipse.class);
            proj.getPluginManager().apply(BaselineIdea.class);
            proj.getPluginManager().apply(BaselineErrorProne.class);
            proj.getPluginManager().apply(BaselineVersions.class);
            proj.getPluginManager().apply(BaselineFormat.class);
            proj.getPluginManager().apply(BaselineReproducibility.class);
            proj.getPluginManager().apply(BaselineExactDependencies.class);
            proj.getPluginManager().apply(BaselineReleaseCompatibility.class);
            proj.getPluginManager().apply(BaselineTesting.class);

            // TODO(dfox): enable this when it has been validated on a few real projects
            // proj.getPluginManager().apply(BaselineClassUniquenessPlugin.class);
        });
    }
}
