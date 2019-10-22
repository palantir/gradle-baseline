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

import com.palantir.baseline.tasks.dependencies.DependencyFinderTask;
import com.palantir.baseline.tasks.dependencies.DependencyReportTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

/** Validates that java projects declare exactly the dependencies they rely on, no more and no less. */
public final class BaselineDependencyPluginv2 implements Plugin<Project> {
    public static final String GROUP_NAME = "Dependency Analysis";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            SourceSet mainSourceSet = project.getConvention()
                    .getPlugin(JavaPluginConvention.class)
                    .getSourceSets()
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            Provider<DependencyFinderTask> findMainDepsTask = createDependencyFinderTask(project, mainSourceSet);
            Provider<DependencyReportTask> analyzeMainDepsTask = createDependencyReportTask(project, mainSourceSet,
                    findMainDepsTask);
        });
    }

    private Provider<DependencyFinderTask> createDependencyFinderTask(Project project, SourceSet sourceSet) {
        String taskName = sourceSet.getTaskName("find", "deps");
        return project.getTasks().register(taskName, DependencyFinderTask.class, t -> {
            t.dependsOn(sourceSet.getClassesTaskName());
            t.setDescription("Produces listings in dot-file format with dependencies that are directly used by the "
                            + sourceSet.getName() + " source set.");
            t.setGroup(GROUP_NAME);
            t.getClassesDir().set(sourceSet.getOutput().getClassesDirs().getSingleFile());
        });
    }

    private Provider<DependencyReportTask> createDependencyReportTask(Project project, SourceSet sourceSet,
                                                                      Provider<DependencyFinderTask> finderTask) {
        String taskName = sourceSet.getTaskName("analyze", "deps");
        return project.getTasks().register(taskName, DependencyReportTask.class, t -> {
            t.setDescription("Produces a report for dependencies of the " + sourceSet.getName() + " source set.");
            t.setGroup(GROUP_NAME);
            t.getDotFileDir().set(finderTask.get().getReportDir());
            ConfigurationContainer configs = project.getConfigurations();
            t.getConfigurations().add(configs.getByName(sourceSet.getCompileClasspathConfigurationName()));
            t.getSourceOnlyConfigurations().add(configs.getByName(sourceSet.getAnnotationProcessorConfigurationName()));
            t.getSourceOnlyConfigurations().add(configs.getByName(sourceSet.getCompileOnlyConfigurationName()));
        });
    }
}
