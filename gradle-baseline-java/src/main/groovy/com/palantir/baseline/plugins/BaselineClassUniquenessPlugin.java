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

import com.google.common.collect.ImmutableList;
import com.palantir.baseline.tasks.CheckClassUniquenessLockTask;
import java.util.List;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * This plugin is similar to https://github.com/nebula-plugins/gradle-lint-plugin/wiki/Duplicate-Classes-Rule but goes
 * one step further and actually hashes any identically named classfiles to figure out if they're <i>completely</i>
 * identical (and therefore safely interchangeable).
 *
 * <p>The task only fails if it finds classes which have the same name but different implementations.
 */
public class BaselineClassUniquenessPlugin extends AbstractBaselinePlugin {
    @Override
    public final void apply(Project project) {
        TaskProvider<CheckClassUniquenessLockTask> checkClassUniqueness =
                project.getTasks().register("checkClassUniqueness", CheckClassUniquenessLockTask.class);
        project.getPlugins().apply(LifecycleBasePlugin.class);
        project.getTasks().getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(checkClassUniqueness);

        project.getPlugins().withId("java", plugin -> {
            checkClassUniqueness.configure(t -> {
                Configuration runtimeClasspath =
                        project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
                t.configurations.add(runtimeClasspath);

                // runtimeClasspath might contain jars which are 'builtBy' other tasks, for example conjure-generated
                // objects. This dependsOn ensures that all those pre-requisite tasks get invoked first, otherwise
                // we see log.info warnings about missing jars e.g. 'Skipping non-existent jar foo-api-objects.jar'
                t.dependsOn(runtimeClasspath);
            });
        });

        // Wire up dependencies so running `./gradlew --write-locks` will update the lock file
        StartParameter startParam = project.getGradle().getStartParameter();
        if (startParam.isWriteDependencyLocks()
                && !startParam.getTaskNames().contains(checkClassUniqueness.getName())) {
            List<String> taskNames = ImmutableList.<String>builder()
                    .addAll(startParam.getTaskNames())
                    .add(checkClassUniqueness.getName())
                    .build();
            startParam.setTaskNames(taskNames);
        }
    }
}
