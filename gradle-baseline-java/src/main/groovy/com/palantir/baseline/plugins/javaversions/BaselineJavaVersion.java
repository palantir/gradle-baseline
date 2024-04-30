/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins.javaversions;

import java.util.Collections;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaDoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.GradleVersion;

public final class BaselineJavaVersion implements Plugin<Project> {

    public static final String EXTENSION_NAME = "javaVersion";
    public static final Logger log = Logging.getLogger(BaselineJavaVersion.class.getName());

    @Override
    public void apply(Project project) {
        BaselineJavaVersionExtension extension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionExtension.class, project);

        project.getPluginManager().withPlugin("java", unused -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);

            JavaToolchainService toolchainService = project.getExtensions().getByType(JavaToolchainService.class);

            // Set the default project toolchain to the runtime target version, this indirectly
            // sets the value returned by 'getTargetCompatibility', which is used by sls-packaging
            // to request a specific java feature release.
            javaPluginExtension.toolchain(new Action<JavaToolchainSpec>() {
                @Override
                public void execute(JavaToolchainSpec javaToolchainSpec) {
                    javaToolchainSpec
                            .getLanguageVersion()
                            .set(extension.runtime().map(ChosenJavaVersion::javaLanguageVersion));
                }
            });

            BaselineJavaVersionsExtension rootExtension =
                    project.getRootProject().getExtensions().getByType(BaselineJavaVersionsExtension.class);

            JavaToolchains baselineConfiguredJavaToolchains = new JavaToolchains(project, rootExtension);

            // Compilation tasks (using target version)
            configureCompilationTasks(
                    project, extension.target(), baselineConfiguredJavaToolchains, rootExtension, toolchainService);

            // Execution tasks (using the runtime version)
            configureExecutionTasks(
                    project, extension.runtime(), baselineConfiguredJavaToolchains, rootExtension, toolchainService);

            // Validation
            TaskProvider<CheckJavaVersionsTask> checkJavaVersions = project.getTasks()
                    .register("checkJavaVersions", CheckJavaVersionsTask.class, new Action<CheckJavaVersionsTask>() {
                        @Override
                        public void execute(CheckJavaVersionsTask task) {
                            task.getTargetVersion().set(extension.target());
                            task.getRuntimeVersion().set(extension.runtime());
                        }
                    });

            TaskProvider<CheckClasspathCompatible> checkRuntimeClasspathCompatible = project.getTasks()
                    .register(
                            "checkRuntimeClasspathCompatible",
                            CheckClasspathCompatible.class,
                            new Action<CheckClasspathCompatible>() {
                                @Override
                                public void execute(CheckClasspathCompatible task) {
                                    task.getClasspathName().set("runtime");
                                    task.getJavaVersion().set(extension.runtime());
                                    task.getClasspath()
                                            .setFrom(project.getConfigurations().getByName("runtimeClasspath"));
                                }
                            });

            project.getTasks().named("check").configure(check -> {
                check.dependsOn(checkJavaVersions, checkRuntimeClasspathCompatible);
            });
        });
    }

    private static void configureCompilationTasks(
            Project project,
            Property<ChosenJavaVersion> target,
            JavaToolchains baselineConfiguredJavaToolchains,
            BaselineJavaVersionsExtension rootExtension,
            JavaToolchainService javaToolchainService) {

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompileTask -> {
            setJavaCompiler(
                    javaCompileTask, rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target);
            javaCompileTask.getOptions().getCompilerArgumentProviders().add(new EnablePreviewArgumentProvider(target));
            // Set sourceCompatibility to opt out of '-release', allowing opens/exports to be used.
            javaCompileTask.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    ((JavaCompile) task)
                            .setSourceCompatibility(
                                    target.get().javaLanguageVersion().toString());
                }
            });
        });

        project.getTasks().withType(Javadoc.class).configureEach(javadocTask -> {
            setJavaDocTool(javadocTask, rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target);

            // javadocTask doesn't allow us to add a CommandLineArgumentProvider, so we do it just in time
            javadocTask.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    if (target.get().enablePreview()) {
                        // yes, javadoc truly takes a single-dash where everyone else takes a double dash
                        ((Javadoc) task).getOptions().addBooleanOption("-enable-preview", true);
                        ((Javadoc) task)
                                .getOptions()
                                .setSource(target.get().javaLanguageVersion().toString());
                    }
                }
            });
        });
        // checkstyle.getJavaLauncher() was added in Gradle 7.5
        if (GradleVersion.current().compareTo(GradleVersion.version("7.5")) >= 0) {
            project.getTasks().withType(Checkstyle.class).configureEach(checkstyle -> checkstyle
                    .getJavaLauncher()
                    .set(getJavaLauncher(
                            rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target)));
        }

        project.getTasks().withType(GroovyCompile.class).configureEach(groovyCompileTask -> {
            groovyCompileTask
                    .getJavaLauncher()
                    .set(getJavaLauncher(
                            rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target));
            groovyCompileTask
                    .getOptions()
                    .getCompilerArgumentProviders()
                    .add(new EnablePreviewArgumentProvider(target));

            // Set sourceCompatibility to opt out of '-release', allowing opens/exports to be used.
            groovyCompileTask.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    ((GroovyCompile) task)
                            .setSourceCompatibility(
                                    target.get().javaLanguageVersion().toString());
                }
            });
        });

        project.getTasks().withType(ScalaCompile.class).configureEach(scalaCompileTask -> {
            scalaCompileTask
                    .getJavaLauncher()
                    .set(getJavaLauncher(
                            rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target));
            scalaCompileTask.getOptions().getCompilerArgumentProviders().add(new EnablePreviewArgumentProvider(target));

            // Set sourceCompatibility to opt out of '-release', allowing opens/exports to be used.
            scalaCompileTask.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    ((ScalaCompile) task)
                            .setSourceCompatibility(
                                    target.get().javaLanguageVersion().toString());
                }
            });
        });

        project.getTasks().withType(ScalaDoc.class).configureEach(scalaDoc -> scalaDoc.getJavaLauncher()
                .set(getJavaLauncher(rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target)));
    }

    private static void configureExecutionTasks(
            Project project,
            Property<ChosenJavaVersion> runtime,
            JavaToolchains baselineConfiguredJavaToolchains,
            BaselineJavaVersionsExtension rootExtension,
            JavaToolchainService javaToolchainService) {

        project.getTasks().withType(JavaExec.class).configureEach(javaExec -> {
            javaExec.getJavaLauncher()
                    .set(getJavaLauncher(
                            rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, runtime));
            javaExec.getJvmArgumentProviders().add(new EnablePreviewArgumentProvider(runtime));
        });

        project.getTasks().withType(Test.class).configureEach(test -> {
            test.getJavaLauncher()
                    .set(getJavaLauncher(
                            rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, runtime));
            test.getJvmArgumentProviders().add(new EnablePreviewArgumentProvider(runtime));
        });
    }

    private static void setJavaCompiler(
            JavaCompile javaCompileTask,
            BaselineJavaVersionsExtension rootExtension,
            JavaToolchains baselineConfiguredJavaToolchains,
            JavaToolchainService javaToolchainService,
            Property<ChosenJavaVersion> version) {
        if (rootExtension.getSetupJdkToolchains().get()) {
            log.debug("Using baselineConfiguredJavaToolchains to configure the javaCompileTask");
            javaCompileTask
                    .getJavaCompiler()
                    .set(baselineConfiguredJavaToolchains
                            .forVersion(version)
                            .flatMap(BaselineJavaToolchain::javaCompiler));
            return;
        }
        log.debug("Using detected javaToolchains to configure the javaCompileTask");
        javaCompileTask.getJavaCompiler().set(javaToolchainService.compilerFor(spec -> spec.getLanguageVersion()
                .set(version.map(ChosenJavaVersion::javaLanguageVersion))));
    }

    private static void setJavaDocTool(
            Javadoc javadocTask,
            BaselineJavaVersionsExtension rootExtension,
            JavaToolchains baselineConfiguredJavaToolchains,
            JavaToolchainService javaToolchainService,
            Property<ChosenJavaVersion> version) {
        if (rootExtension.getSetupJdkToolchains().get()) {
            log.debug("Using baselineConfiguredJavaToolchains to configure javaDocTool");
            javadocTask
                    .getJavadocTool()
                    .set(baselineConfiguredJavaToolchains
                            .forVersion(version)
                            .flatMap(BaselineJavaToolchain::javadocTool));
            return;
        }
        log.debug("Using detected javaToolchains to configure javaDocTool");
        javadocTask.getJavadocTool().set(javaToolchainService.javadocToolFor(spec -> spec.getLanguageVersion()
                .set(version.map(ChosenJavaVersion::javaLanguageVersion))));
    }

    private static Provider<JavaLauncher> getJavaLauncher(
            BaselineJavaVersionsExtension rootExtension,
            JavaToolchains baselineConfiguredJavaToolchains,
            JavaToolchainService javaToolchainService,
            Property<ChosenJavaVersion> version) {
        if (rootExtension.getSetupJdkToolchains().get()) {
            log.debug("Using baselineConfiguredJavaToolchains to configure JavaLauncher");
            return baselineConfiguredJavaToolchains.forVersion(version).flatMap(BaselineJavaToolchain::javaLauncher);
        }
        log.debug("Using detected javaToolchains to configure JavaLauncher");
        return javaToolchainService.launcherFor(
                spec -> spec.getLanguageVersion().set(version.map(ChosenJavaVersion::javaLanguageVersion)));
    }

    @CacheableTask
    @SuppressWarnings("checkstyle:DesignForExtension")
    public static class CheckJavaVersionsTask extends DefaultTask {

        private final Property<ChosenJavaVersion> targetVersion;
        private final Property<ChosenJavaVersion> runtimeVersion;

        @Inject
        public CheckJavaVersionsTask() {
            setGroup("Verification");
            setDescription("Ensures configured java versions are compatible: "
                    + "The runtime version must be greater than or equal to the target version.");
            targetVersion = getProject().getObjects().property(ChosenJavaVersion.class);
            runtimeVersion = getProject().getObjects().property(ChosenJavaVersion.class);
        }

        @Input
        public Property<ChosenJavaVersion> getTargetVersion() {
            return targetVersion;
        }

        @Input
        public Property<ChosenJavaVersion> getRuntimeVersion() {
            return runtimeVersion;
        }

        @TaskAction
        public final void checkJavaVersions() {
            ChosenJavaVersion target = getTargetVersion().get();
            ChosenJavaVersion runtime = getRuntimeVersion().get();
            getLogger()
                    .debug(
                            "BaselineJavaVersion configured project {} with target version {} and runtime version {}",
                            getProject(),
                            target,
                            runtime);

            if (target.enablePreview() && !target.equals(runtime)) {
                throw new GradleException(String.format(
                        "Runtime Java version (%s) must be exactly the same as the compilation target (%s), because "
                                + "--enable-preview is enabled. Otherwise Java will fail to start. See "
                                + "https://openjdk.org/jeps/12.",
                        runtime, target));
            }

            if (target.javaLanguageVersion().asInt()
                    > runtime.javaLanguageVersion().asInt()) {
                throw new GradleException(String.format(
                        "The requested compilation target Java version (%s) must not "
                                + "exceed the requested runtime Java version (%s)",
                        target, runtime));
            }
        }
    }

    private static class EnablePreviewArgumentProvider implements CommandLineArgumentProvider {

        public static final String FLAG = "--enable-preview";

        private final Provider<ChosenJavaVersion> provider;

        private EnablePreviewArgumentProvider(Provider<ChosenJavaVersion> provider) {
            this.provider = provider;
        }

        @Override
        public Iterable<String> asArguments() {
            return provider.get().enablePreview() ? Collections.singletonList(FLAG) : Collections.emptyList();
        }
    }
}
