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

import com.diffplug.gradle.spotless.SpotlessExtension;
import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

class BaselineFormat extends AbstractBaselinePlugin {

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().withPlugin("java", plugin -> {
            project.getPluginManager().apply("com.diffplug.gradle.spotless");

            project.getExtensions().getByType(SpotlessExtension.class).java(java -> {
                // Configure a lazy FileCollection then pass it as the target
                ConfigurableFileCollection allJavaFiles = project.files();
                project
                        .getConvention()
                        .getPlugin(JavaPluginConvention.class)
                        .getSourceSets()
                        .all(sourceSet -> allJavaFiles.from(nonGeneratedJava(project, sourceSet)));

                java.target(allJavaFiles);
                java.removeUnusedImports();
                // use empty string to specify one group for all non-static imports
                java.importOrder("");
                java.trimTrailingWhitespace();
                java.indentWithSpaces(4);
                java.endWithNewline();
            });

            // necessary because SpotlessPlugin creates tasks in an afterEvaluate block
            Task formatTask = project.task("format");
            project.afterEvaluate(p -> formatTask.dependsOn(project.getTasks().getByName("spotlessApply")));
        });
    }

    private static FileCollection nonGeneratedJava(Project project, SourceSet sourceSet) {
        return sourceSet.getAllJava().filter(file -> {
            Path projectScopedPath = project.getProjectDir().toPath().relativize(file.toPath());
            return !projectScopedPath.toString().contains("generated");
        });
    }
}
