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

import com.palantir.baseline.tasks.ClassUniquenessAnalyzer;
import java.io.File;
import java.nio.file.Files;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.SetProperty;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.GFileUtils;

/**
 * This plugin is similar to https://github.com/nebula-plugins/gradle-lint-plugin/wiki/Duplicate-Classes-Rule
 * but goes one step further and actually hashes any identically named classfiles to figure out if they're
 * <i>completely</i> identical (and therefore safely interchangeable).
 *
 * The task only fails if it finds classes which have the same name but different implementations.
 */
public class BaselineClassUniquenessLockPlugin extends AbstractBaselinePlugin {
    @Override
    public final void apply(Project project) {

        // TODO(dfox): expose this so that users can add/remove their own configurations?
        SetProperty<String> configurationNames = project.getObjects().setProperty(String.class);
        project.getPlugins().withId("java", plugin -> {
            configurationNames.add("runtimeClasspath");
        });

        File lockFile = project.file("baseline-class-uniqueness.lock");

        Task checkClassUniquenessLock = project.getTasks().create("checkClassUniquenessLock");
        checkClassUniquenessLock.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("# Run ./gradlew checkClassUniquenessLock --write-locks to update this file\n");

                for (String configurationName : configurationNames.get()) {
                    stringBuilder.append("[" + configurationName + "]\n");
                    ClassUniquenessAnalyzer analyzer = new ClassUniquenessAnalyzer(project.getLogger());

                    Configuration configuration = project.getConfigurations().getByName(configurationName);
                    analyzer.analyzeConfiguration(configuration);
                    stringBuilder.append(analyzer.getProblemJars().toString());
                    stringBuilder.append('\n');
                    stringBuilder.append('\n');
                }

                String expected = stringBuilder.toString();

                if (project.getGradle().getStartParameter().isWriteDependencyLocks()) {
                    // just nuke whatever was there before
                    GFileUtils.writeFile(expected, lockFile);
                } else {
                    String onDisk = GFileUtils.readFile(lockFile);
                    if (!onDisk.equals(expected)) {
                        throw new GradleException(lockFile + " is out of date, please run ./gradlew "
                                + "checkClassUniquenessLock --write-locks to update this file");
                    }
                }
            }
        });
        // TODO(dfox): up-to-dateness

        project.getPlugins().apply(LifecycleBasePlugin.class);
        project.getTasks().getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(checkClassUniquenessLock);
    }
}
