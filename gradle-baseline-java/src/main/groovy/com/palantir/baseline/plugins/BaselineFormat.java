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

class BaselineFormat extends AbstractBaselinePlugin {

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().withPlugin("java", plugin -> {
            project.getPluginManager().apply("com.diffplug.gradle.spotless");

            project.getExtensions().getByType(SpotlessExtension.class).java(java -> {
                // TODO(dfox): apply this to all source sets, not just 'main' and 'test'
                java.target("src/main/java/**/*.java", "src/main/test/**/*.java");
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
}
