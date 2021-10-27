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
import java.io.File;
import java.util.Collections;
import java.util.Objects;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;

public final class BaselineGoethe implements Plugin<Project> {

    private static final ImmutableList<String> GOETHE_ARGS = ImmutableList.of(
            "--add-exports",
            "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports",
            "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports",
            "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports",
            "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports",
            "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                project.getTasks()
                        .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                        .get()
                        .getOptions()
                        .getCompilerArgumentProviders()
                        // Use an anonymous class because tasks with lambda inputs cannot be cached
                        .add(new CommandLineArgumentProvider() {
                            @Override
                            public Iterable<String> asArguments() {
                                if (hasGoetheProcessor(project, sourceSet)) {
                                    return GOETHE_ARGS;
                                }
                                return Collections.emptyList();
                            }
                        });
            });

            project.getTasks().withType(Test.class, new Action<Test>() {

                @Override
                public void execute(Test test) {
                    test.getJvmArgumentProviders().add(new CommandLineArgumentProvider() {

                        @Override
                        public Iterable<String> asArguments() {
                            if (hasGoetheJar(test.getClasspath())) {
                                return GOETHE_ARGS;
                            }
                            return Collections.emptyList();
                        }
                    });
                }
            });
        });
    }

    private static boolean hasGoetheProcessor(Project project, SourceSet sourceSet) {
        return project
                .getConfigurations()
                .getByName(sourceSet.getAnnotationProcessorConfigurationName())
                .getDependencies()
                .stream()
                .anyMatch(BaselineGoethe::isGoetheValue);
    }

    private static boolean hasGoetheJar(FileCollection classpath) {
        return !classpath
                .filter(new Spec<File>() {
                    @Override
                    public boolean isSatisfiedBy(File element) {
                        return element.getName().startsWith("goethe-")
                                && element.getName().endsWith(".jar");
                    }
                })
                .isEmpty();
    }

    private static boolean isGoetheValue(Dependency dep) {
        return Objects.equals(dep.getGroup(), "com.palantir.goethe") && Objects.equals(dep.getName(), "goethe");
    }
}
