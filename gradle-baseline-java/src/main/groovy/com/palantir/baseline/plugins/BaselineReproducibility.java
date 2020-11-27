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

import com.palantir.baseline.tasks.CheckExplicitSourceCompatibilityTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.GradleVersion;

/** Sensible defaults so that all Jar, Tar, Zip tasks can be deterministically reproduced. */
public final class BaselineReproducibility implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(t -> {
            t.setPreserveFileTimestamps(false);
            t.setReproducibleFileOrder(true);
            t.setDuplicatesStrategy(DuplicatesStrategy.WARN);
        });

        project.getPluginManager().withPlugin("nebula.info", plugin -> {
            project.getLogger()
                    .warn(
                            "Please remove the 'nebula.info' plugin from {} as it breaks "
                                    + "reproducibility of jars by adding a 'Build-Date' entry to the MANIFEST.MF",
                            project);
        });

        project.getPlugins().withType(JavaBasePlugin.class, _plugin -> {
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

            // just being a bit defensive because we need to cast to an internal gradle class... don't wanna block
            // upgrades
            String clazz = javaConvention.getClass().getCanonicalName();
            String expected = "org.gradle.api.plugins.internal.DefaultJavaPluginConvention";
            if (!clazz.equals(expected)) {
                project.getLogger()
                        .error(
                                "BaselineReproducibility unable to check sourceCompatibility - please report this to "
                                        + "https://github.com/palantir/gradle-baseline/issues and mention the current"
                                        + " Gradle version ({}). Expected '{}' found '{}'",
                                GradleVersion.current(),
                                expected,
                                clazz);
                return;
            }

            TaskProvider<? extends Task> checkExplicitSourceCompatibility = project.getTasks()
                    .register("checkExplicitSourceCompatibility", CheckExplicitSourceCompatibilityTask.class);

            project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(check -> {
                check.dependsOn(checkExplicitSourceCompatibility);
            });
        });
    }
}
