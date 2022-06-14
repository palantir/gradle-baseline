/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import java.util.Collections;
import java.util.Objects;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

public final class BaselineImmutables implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                project.getTasks()
                        .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                        .configure(javaCompileTask -> {
                            javaCompileTask
                                    .getOptions()
                                    .getCompilerArgumentProviders()
                                    // Use an anonymous class because tasks with lambda inputs cannot be cached
                                    .add(new CommandLineArgumentProvider() {
                                        @Override
                                        public Iterable<String> asArguments() {
                                            if (hasImmutablesProcessor(project, sourceSet)) {
                                                return Collections.singletonList("-Aimmutables.gradle.incremental");
                                            }

                                            return Collections.emptyList();
                                        }
                                    });
                        });
            });
        });
    }

    private static boolean hasImmutablesProcessor(Project project, SourceSet sourceSet) {
        return project
                .getConfigurations()
                .getByName(sourceSet.getAnnotationProcessorConfigurationName())
                .getDependencies()
                .stream()
                .anyMatch(BaselineImmutables::isImmutablesValue);
    }

    private static boolean isImmutablesValue(Dependency dep) {
        return Objects.equals(dep.getGroup(), "org.immutables") && Objects.equals(dep.getName(), "value");
    }
}
