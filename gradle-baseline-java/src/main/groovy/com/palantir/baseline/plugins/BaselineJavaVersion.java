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
import com.palantir.baseline.extensions.BaselineJavaVersionsExtension;
import com.palantir.baseline.plugins.javaversions.AzulJdkDownloader;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionRootPlugin;
import com.palantir.baseline.plugins.javaversions.JavaToolchains;
import com.palantir.baseline.plugins.javaversions.PalantirJavaToolchain;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaDoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

public final class BaselineJavaVersion implements Plugin<Project> {

    public static final String EXTENSION_NAME = "javaVersion";

    @Override
    public void apply(Project project) {
        BaselineJavaVersionExtension extension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionExtension.class, project);

        AzulJdkDownloader jdkDownloader = project.getRootProject()
                .getPlugins()
                .apply(BaselineJavaVersionRootPlugin.class)
                .jdkDownloader();

        project.getPluginManager().withPlugin("java", unused -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);

            // Set the default project toolchain to the runtime target version, this indirectly
            // sets the value returned by 'getTargetCompatibility', which is used by sls-packaging
            // to request a specific java feature release.
            javaPluginExtension.toolchain(new Action<JavaToolchainSpec>() {
                @Override
                public void execute(JavaToolchainSpec javaToolchainSpec) {
                    javaToolchainSpec.getLanguageVersion().set(extension.runtime());
                }
            });

            JavaToolchains javaToolchains = new JavaToolchains(
                    project,
                    project.getRootProject().getExtensions().getByType(BaselineJavaVersionsExtension.class),
                    jdkDownloader);

            // Compilation tasks (using target version)
            configureCompilationTasks(project, extension.target(), javaToolchains.forVersion(extension.target()));

            // Execution tasks (using the runtime version)
            configureExecutionTasks(project, javaToolchains.forVersion(extension.runtime()));

            // Validation
            project.getTasks()
                    .register("checkJavaVersions", CheckJavaVersionsTask.class, new Action<CheckJavaVersionsTask>() {
                        @Override
                        public void execute(CheckJavaVersionsTask task) {
                            task.getTargetVersion().set(extension.target());
                            task.getRuntimeVersion().set(extension.runtime());
                            project.getTasks().getByName("check").dependsOn(task);
                        }
                    });
        });
    }

    private static void configureCompilationTasks(
            Project project,
            Provider<JavaLanguageVersion> targetVersionProvider,
            Provider<PalantirJavaToolchain> javaToolchain) {
        project.getTasks().withType(JavaCompile.class, new Action<JavaCompile>() {
            @Override
            public void execute(JavaCompile javaCompile) {
                javaCompile.getJavaCompiler().set(javaToolchain.flatMap(PalantirJavaToolchain::javaCompiler));
                // Set sourceCompatibility to opt out of '-release', allowing opens/exports to be used.
                javaCompile.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        ((JavaCompile) task)
                                .setSourceCompatibility(
                                        targetVersionProvider.get().toString());
                    }
                });
            }
        });

        project.getTasks().withType(Javadoc.class, new Action<Javadoc>() {
            @Override
            public void execute(Javadoc javadoc) {
                javadoc.getJavadocTool().set(javaToolchain.flatMap(PalantirJavaToolchain::javadocTool));
            }
        });

        project.getTasks().withType(GroovyCompile.class, new Action<GroovyCompile>() {
            @Override
            public void execute(GroovyCompile groovyCompile) {
                groovyCompile.getJavaLauncher().set(javaToolchain.flatMap(PalantirJavaToolchain::javaLauncher));
                // Set sourceCompatibility to opt out of '-release', allowing opens/exports to be used.
                groovyCompile.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        ((GroovyCompile) task)
                                .setSourceCompatibility(
                                        targetVersionProvider.get().toString());
                    }
                });
            }
        });

        project.getTasks().withType(ScalaCompile.class, new Action<ScalaCompile>() {
            @Override
            public void execute(ScalaCompile scalaCompile) {
                scalaCompile.getJavaLauncher().set(javaToolchain.flatMap(PalantirJavaToolchain::javaLauncher));
                // Set sourceCompatibility to opt out of '-release', allowing opens/exports to be used.
                scalaCompile.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        ((ScalaCompile) task)
                                .setSourceCompatibility(
                                        targetVersionProvider.get().toString());
                    }
                });
            }
        });

        project.getTasks().withType(ScalaDoc.class, new Action<ScalaDoc>() {
            @Override
            public void execute(ScalaDoc scalaDoc) {
                scalaDoc.getJavaLauncher().set(javaToolchain.flatMap(PalantirJavaToolchain::javaLauncher));
            }
        });
    }

    private static void configureExecutionTasks(Project project, Provider<PalantirJavaToolchain> javaToolchain) {
        project.getTasks().withType(JavaExec.class, new Action<JavaExec>() {
            @Override
            public void execute(JavaExec javaExec) {
                javaExec.getJavaLauncher().set(javaToolchain.flatMap(PalantirJavaToolchain::javaLauncher));
            }
        });

        project.getTasks().withType(Test.class, new Action<Test>() {
            @Override
            public void execute(Test test) {
                test.getJavaLauncher().set(javaToolchain.flatMap(PalantirJavaToolchain::javaLauncher));
            }
        });
    }

    @CacheableTask
    @SuppressWarnings("checkstyle:DesignForExtension")
    public static class CheckJavaVersionsTask extends DefaultTask {

        private final Property<JavaLanguageVersion> targetVersion;
        private final Property<JavaLanguageVersion> runtimeVersion;

        @Inject
        public CheckJavaVersionsTask() {
            setGroup("Verification");
            setDescription("Ensures configured java versions are compatible: "
                    + "The runtime version must be greater than or equal to the target version.");
            targetVersion = getProject().getObjects().property(JavaLanguageVersion.class);
            runtimeVersion = getProject().getObjects().property(JavaLanguageVersion.class);
        }

        @Input
        public Property<JavaLanguageVersion> getTargetVersion() {
            return targetVersion;
        }

        @Input
        public Property<JavaLanguageVersion> getRuntimeVersion() {
            return runtimeVersion;
        }

        @TaskAction
        public final void checkJavaVersions() {
            JavaLanguageVersion target = getTargetVersion().get();
            JavaLanguageVersion runtime = getRuntimeVersion().get();
            getLogger()
                    .debug(
                            "BaselineJavaVersion configured project {} with target version {} and runtime version {}",
                            getProject(),
                            target,
                            runtime);
            if (target.asInt() > runtime.asInt()) {
                throw new GradleException(String.format(
                        "The requested compilation target Java version (%s) must not "
                                + "exceed the requested runtime Java version (%s)",
                        target, runtime));
            }
        }
    }
}
