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

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Objects;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

public final class BaselineImmutables implements Plugin<Project> {

    private static final ImmutableList<String> GRADLE_INCREMENTAL = ImmutableList.of("-Aimmutables.gradle.incremental");
    // See https://github.com/immutables/immutables/issues/1379
    private static final ImmutableList<String> EXPORTS =
            ImmutableList.of("--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED");

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                project.getTasks()
                        .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                        .configure(javaCompileTask -> {
                            Provider<Boolean> hasImmutablesProvider = project.provider(() -> project
                                    .getConfigurations()
                                    .getByName(sourceSet.getAnnotationProcessorConfigurationName())
                                    .getIncoming()
                                    .getArtifacts()
                                    .getResolvedArtifacts()
                                    .get()
                                    .stream()
                                    .anyMatch(BaselineImmutables::isImmutablesValue));

                            javaCompileTask.getInputs().property("hasImmutables", hasImmutablesProvider);

                            javaCompileTask
                                    .getOptions()
                                    .getCompilerArgumentProviders()
                                    .add(new CommandLineArgumentProvider() {
                                        @Override
                                        public Iterable<String> asArguments() {
                                            return hasImmutablesProvider.get()
                                                    ? GRADLE_INCREMENTAL
                                                    : Collections.emptyList();
                                        }
                                    });

                            javaCompileTask
                                    .getOptions()
                                    .getForkOptions()
                                    .getJvmArgumentProviders()
                                    .add(new CommandLineArgumentProvider() {
                                        @Override
                                        public Iterable<String> asArguments() {
                                            return hasImmutablesProvider.get() ? EXPORTS : Collections.emptyList();
                                        }
                                    });
                        });
            });
        });
    }

    private static boolean isImmutablesValue(ResolvedArtifactResult resolvedArtifact) {
        ComponentIdentifier id = resolvedArtifact.getId().getComponentIdentifier();

        if (!(id instanceof ModuleComponentIdentifier moduleId)) {
            return false;
        }

        // Previously, we used the classifier to distinguish between the processor jar and the annotations jar.
        // However, ResolvedArtifactResult does not expose a classifier property, so we now check the file name
        // directly.
        return Objects.equals(moduleId.getGroup(), "org.immutables")
                && Objects.equals(moduleId.getModule(), "value")
                && !resolvedArtifact.getFile().getName().contains("-annotations");
    }
}
