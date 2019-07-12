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
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;

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
                        .all(sourceSet -> allJavaFiles.from(
                                sourceSet.getAllJava().filter(file -> !file.toString().contains("/generated"))));

                java.target(allJavaFiles);
                java.removeUnusedImports();
                // use empty string to specify one group for all non-static imports
                java.importOrder("");
                java.trimTrailingWhitespace();
                java.indentWithSpaces(4);
                java.endWithNewline();

                // No empty lines at start of blocks
                java.replaceRegex("Block starts with blank lines", "\\) \\{\n+", ") {\n");
            });

            // necessary because SpotlessPlugin creates tasks in an afterEvaluate block
            Task formatTask = project.task("format");
            project.afterEvaluate(p -> {
                Task spotlessApply = project.getTasks().getByName("spotlessApply");
                formatTask.dependsOn(spotlessApply);
                project.getTasks().withType(JavaCompile.class).configureEach(spotlessApply::mustRunAfter);
            });
        });
    }
}
