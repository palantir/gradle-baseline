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
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaDoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
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

            // Set the default project toolchain to the compilation target version, this indirectly
            // sets the value returned by 'getTargetCompatibility'
            javaPluginExtension.toolchain(new Action<JavaToolchainSpec>() {
                @Override
                public void execute(JavaToolchainSpec javaToolchainSpec) {
                    javaToolchainSpec.getLanguageVersion().set(extension.target());
                }
            });

            // Compilation tasks (using target version)
            configureCompilationTasks(project, extension.target());

            // Execution tasks (using the runtime version)
            configureExecutionTasks(project, extension.runtime());
        });
    }

    private static void configureCompilationTasks(
            Project project, Provider<JavaLanguageVersion> targetVersionProvider) {
        JavaToolchainService javaToolchainService = project.getExtensions().getByType(JavaToolchainService.class);

        project.getTasks().withType(JavaCompile.class, new Action<JavaCompile>() {
            @Override
            public void execute(JavaCompile javaCompile) {
                javaCompile.getJavaCompiler().set(javaToolchainService.compilerFor(new Action<JavaToolchainSpec>() {
                    @Override
                    public void execute(JavaToolchainSpec javaToolchainSpec) {
                        javaToolchainSpec.getLanguageVersion().set(targetVersionProvider);
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
                        javaToolchainSpec.getLanguageVersion().set(targetVersionProvider);
                    }
                }));
            }
        });

        project.getTasks().withType(GroovyCompile.class, new Action<GroovyCompile>() {
            @Override
            public void execute(GroovyCompile groovyCompile) {
                groovyCompile.getJavaLauncher().set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                    @Override
                    public void execute(JavaToolchainSpec javaToolchainSpec) {
                        javaToolchainSpec.getLanguageVersion().set(targetVersionProvider);
                    }
                }));
            }
        });

        project.getTasks().withType(ScalaCompile.class, new Action<ScalaCompile>() {
            @Override
            public void execute(ScalaCompile scalaCompile) {
                scalaCompile.getJavaLauncher().set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                    @Override
                    public void execute(JavaToolchainSpec javaToolchainSpec) {
                        javaToolchainSpec.getLanguageVersion().set(targetVersionProvider);
                    }
                }));
            }
        });

        project.getTasks().withType(ScalaDoc.class, new Action<ScalaDoc>() {
            @Override
            public void execute(ScalaDoc scalaDoc) {
                scalaDoc.getJavaLauncher().set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                    @Override
                    public void execute(JavaToolchainSpec javaToolchainSpec) {
                        javaToolchainSpec.getLanguageVersion().set(targetVersionProvider);
                    }
                }));
            }
        });
    }

    private static void configureExecutionTasks(Project project, Provider<JavaLanguageVersion> runtimeVersionProvider) {
        JavaToolchainService javaToolchainService = project.getExtensions().getByType(JavaToolchainService.class);
        project.getTasks().withType(JavaExec.class, new Action<JavaExec>() {
            @Override
            public void execute(JavaExec javaExec) {
                javaExec.getJavaLauncher().set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                    @Override
                    public void execute(JavaToolchainSpec javaToolchainSpec) {
                        javaToolchainSpec.getLanguageVersion().set(runtimeVersionProvider);
                    }
                }));
            }
        });

        project.getTasks().withType(Test.class, new Action<Test>() {
            @Override
            public void execute(Test test) {
                test.getJavaLauncher().set(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                    @Override
                    public void execute(JavaToolchainSpec javaToolchainSpec) {
                        javaToolchainSpec.getLanguageVersion().set(runtimeVersionProvider);
                    }
                }));
            }
        });
    }
}
