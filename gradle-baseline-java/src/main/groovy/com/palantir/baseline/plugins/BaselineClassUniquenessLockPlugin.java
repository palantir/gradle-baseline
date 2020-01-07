/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.baseline.tasks.ClassUniquenessLockTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.SetProperty;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * This plugin is similar to https://github.com/nebula-plugins/gradle-lint-plugin/wiki/Duplicate-Classes-Rule but goes
 * one step further and actually hashes any identically named classfiles to figure out if they're <i>completely</i>
 * identical (and therefore safely interchangeable).
 *
 * <p>The task only fails if it finds classes which have the same name but different implementations.
 */
public class BaselineClassUniquenessLockPlugin extends AbstractBaselinePlugin {
    @Override
    public final void apply(Project project) {

        // TODO(dfox): expose this so that users can add/remove their own configurations?
        SetProperty<String> configurationNames = project.getObjects().setProperty(String.class);
        project.getPlugins().withId("java", plugin -> {
            configurationNames.add("runtimeClasspath");
        });

        // TODO(dfox): up-to-dateness of this task
        Task task = project.getTasks().create("checkClassUniquenessLock", ClassUniquenessLockTask.class);

        project.getPlugins().apply(LifecycleBasePlugin.class);
        project.getTasks().getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task);
    }
}
