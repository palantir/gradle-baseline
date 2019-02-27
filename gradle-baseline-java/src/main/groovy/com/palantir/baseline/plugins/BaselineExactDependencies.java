/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.baseline.tasks.CheckExactDependenciesTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public final class BaselineExactDependencies implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {

            SourceSetContainer sourceSets = project.getConvention()
                    .getPlugin(JavaPluginConvention.class).getSourceSets();
            SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            Configuration compileClasspath = project.getConfigurations()
                    .getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);

            project.getTasks().create("checkExactDependencies", CheckExactDependenciesTask.class, task -> {
                task.dependsOn(JavaPlugin.CLASSES_TASK_NAME);
                task.setClasses(mainSourceSet.getOutput().getClassesDirs());
                task.dependenciesConfiguration(compileClasspath);
            });
        });
    }

}
