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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import netflix.nebula.dependency.recommender.DependencyRecommendationsPlugin;
import netflix.nebula.dependency.recommender.RecommendationStrategies;
import netflix.nebula.dependency.recommender.provider.RecommendationProviderContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class BaselineVersionsProps implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        warnIfAppliedToSubproject(project);

        File versionsPropsFile = rootVersionsPropsFile(project);

        project.getRootProject().allprojects(proj -> {
            // apply plugin: "nebula.dependency-recommender"
            proj.getPluginManager().apply(DependencyRecommendationsPlugin.class);

            // get dependencyRecommendations extension
            RecommendationProviderContainer extension = proj.getExtensions().getByType(
                    RecommendationProviderContainer.class);

            extension.setStrategy(RecommendationStrategies.OverrideTransitives); // default is 'ConflictResolved'
            
            extension.propertiesFile(ImmutableMap.of("file", versionsPropsFile));

            // allow nested projects to specify their own nested versions.props file
            if (proj != proj.getRootProject() && proj.file("versions.props").exists()) {
                extension.propertiesFile(ImmutableMap.of("file", proj.file("versions.props")));
            }
        });
    }

    private static File rootVersionsPropsFile(Project project) {
        File file = project.getRootProject().file("versions.props");
        if (!file.canRead()) {
            try {
                Files.createFile(file.toPath());
            } catch (IOException e) {
                project.getLogger().warn("Unable to create empty versions.props file, please create this manually", e);
            }
        }
        return file;
    }

    private static void warnIfAppliedToSubproject(Project project) {
        if (project != project.getRootProject()) {
            project.getLogger().warn(
                    "com.palantir.baseline-versions-props should be applied to the root project only, not '{}'",
                    project.getName());
        }
    }
}
