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

import com.palantir.baseline.extensions.BaselineJavaVersionExtension;
import java.util.Objects;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

public final class BaselineJavaVersion implements Plugin<Project> {

    public static final String EXTENSION_NAME = "javaVersion";

    @Override
    public void apply(Project project) {
        BaselineJavaVersionExtension extension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionExtension.class, project);
        project.getPluginManager().withPlugin("java", unused -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            JavaToolchainService javaToolchainService = project.getExtensions().getByType(JavaToolchainService.class);

            // Set the default project toolchain
            javaPluginExtension.toolchain(new Action<JavaToolchainSpec>() {
                @Override
                public void execute(JavaToolchainSpec javaToolchainSpec) {
                    javaToolchainSpec.getLanguageVersion().set(extension.runtime());
                }
            });

            project.getTasks().withType(JavaCompile.class, new Action<JavaCompile>() {
                @Override
                public void execute(JavaCompile javaCompile) {
                    javaCompile.getJavaCompiler().set(javaToolchainService.compilerFor(new Action<JavaToolchainSpec>() {
                        @Override
                        public void execute(JavaToolchainSpec javaToolchainSpec) {
                            javaToolchainSpec.getLanguageVersion().set(extension.target());
                        }
                    }));
                }
            });

            project.getTasks().withType(GroovyCompile.class, new Action<GroovyCompile>() {
                @Override
                public void execute(GroovyCompile groovyCompile) {
                    groovyCompile
                            .getJavaLauncher()
                            .set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                                @Override
                                public void execute(JavaToolchainSpec javaToolchainSpec) {
                                    javaToolchainSpec.getLanguageVersion().set(extension.target());
                                }
                            }));
                }
            });

            project.getTasks().withType(ScalaCompile.class, new Action<ScalaCompile>() {
                @Override
                public void execute(ScalaCompile scalaCompile) {
                    scalaCompile
                            .getJavaLauncher()
                            .set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                                @Override
                                public void execute(JavaToolchainSpec javaToolchainSpec) {
                                    javaToolchainSpec.getLanguageVersion().set(extension.target());
                                }
                            }));
                }
            });

            project.getTasks().withType(Javadoc.class, new Action<Javadoc>() {
                @Override
                public void execute(Javadoc javadoc) {
                    javadoc.getJavadocTool().set(javaToolchainService.javadocToolFor(new Action<JavaToolchainSpec>() {
                        @Override
                        public void execute(JavaToolchainSpec javaToolchainSpec) {
                            javaToolchainSpec.getLanguageVersion().set(extension.target());
                        }
                    }));
                }
            });

            TaskCollection<?> testTask = project.getTasks().matching(new Spec<Task>() {
                @Override
                public boolean isSatisfiedBy(Task element) {
                    return "test".equals(element.getName()) && element instanceof Test;
                }
            });
            Task testTargetVersion = project.getTasks().create("testTargetVersion", Test.class, new Action<Test>() {
                @Override
                public void execute(Test test) {
                    // additional test task is only run if runtime and target versions differ
                    test.onlyIf(new Spec<Task>() {
                        @Override
                        public boolean isSatisfiedBy(Task element) {
                            return !Objects.equals(
                                            extension.target().get(),
                                            extension.runtime().get())
                                    && !testTask.isEmpty();
                        }
                    });
                    test.getJavaLauncher().set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                        @Override
                        public void execute(JavaToolchainSpec javaToolchainSpec) {
                            javaToolchainSpec.getLanguageVersion().set(extension.target());
                        }
                    }));
                }
            });
            testTask.configureEach(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.dependsOn(testTargetVersion);
                }
            });
        });
    }
}
