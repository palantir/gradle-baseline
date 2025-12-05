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

import com.google.common.collect.Sets;
import com.palantir.baseline.extensions.BaselineModuleJvmArgsExtension;
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
import org.gradle.api.tasks.Console;
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
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
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
        @SuppressWarnings("for-rollout:GradleTypesAsFields")
        BaselineJavaVersionExtension extension =
                project.getExtensions().create(EXTENSION_NAME, BaselineJavaVersionExtension.class);

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

            // Compilation tasks (using java compiler version for the java compiler, but the targeting the target)
            configureCompilationTasks(
                    project,
                    extension.javaCompiler(),
                    extension.target(),
                    baselineConfiguredJavaToolchains,
                    rootExtension,
                    toolchainService);

            // Execution tasks (using the runtime version)
            configureExecutionTasks(
                    project, extension.runtime(), baselineConfiguredJavaToolchains, rootExtension, toolchainService);

            // Validation
            TaskProvider<CheckJavaVersionsTask> checkJavaVersions = project.getTasks()
                    .register("checkJavaVersions", CheckJavaVersionsTask.class, task -> {
                        task.getJavaCompilerVersion().set(extension.javaCompiler());
                        task.getTargetVersion().set(extension.target());
                        task.getRuntimeVersion().set(extension.runtime());
                    });

            TaskProvider<CheckClasspathCompatible> checkRuntimeClasspathCompatible = project.getTasks()
                    .register("checkRuntimeClasspathCompatible", CheckClasspathCompatible.class, task -> {
                        task.getClasspathName().set("runtime");
                        task.getJavaVersion().set(extension.runtime());
                        task.getClasspath().setFrom(project.getConfigurations().getByName("runtimeClasspath"));
                    });

            project.getTasks().named("check").configure(check -> {
                check.dependsOn(checkJavaVersions, checkRuntimeClasspathCompatible);
            });
        });
    }

    private static void configureCompilationTasks(
            Project project,
            Provider<JavaLanguageVersion> javaCompiler,
            Property<ChosenJavaVersion> target,
            JavaToolchains baselineConfiguredJavaToolchains,
            BaselineJavaVersionsExtension rootExtension,
            JavaToolchainService javaToolchainService) {

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompileTask -> {
            setJavaCompiler(
                    javaCompileTask,
                    rootExtension,
                    baselineConfiguredJavaToolchains,
                    javaToolchainService,
                    // If `javaCompiler` is explicitly set, we use that version for the compiler, else we fall
                    // back to the old behaviour of
                    javaCompiler.orElse(target.map(ChosenJavaVersion::javaLanguageVersion)));

            javaCompileTask.getOptions().getCompilerArgumentProviders().add(new EnablePreviewArgumentProvider(target));

            javaCompileTask
                    .getOptions()
                    .getRelease()
                    // The `.zip(javaCompiler` here ensures that release will be set if and only if javaCompiler
                    // property was set. If it was not set, we use the old behaviour of not setting release.
                    .set(target.map(ChosenJavaVersion::asMajorVersion).zip(javaCompiler, (targetValue, _ignored) -> {
                        // Javac does not allow `--add-exports` and `--release` to be used together. This is
                        // problematic, as quite a lot of code we write (especially compiler plugins like
                        // error-prone checks or palantir-java-format) need to use `--add-exports`. But alas, there's no
                        // way to circumvent this (read more at https://github.com/palantir/gradle-baseline/pull/3376).

                        // So we drop the `--release` flag if `--add-exports` is used, by making the value of this
                        // release property null, aka unset.

                        // The downside of not using `--release` is that if people use a JDK api that is newer than the
                        // compilation target, the compilation will succeed rather than fail. Mitigating factors are:
                        //   * IntelliJ still red-underlines the API usage, even if compilation succeeds
                        //   * Relatively few repos need `--add-exports` (<100 as of writing) and they are mainly
                        //     owned by the Java infrastructure teams rather than regular developers.
                        // So this risk *should* be manageable.

                        // In an ideal world, we'd check the `options.allCompilerArgs` in here to see if
                        // `--add-exports` is added. This means even if some external gradle plugin or some tool that
                        // *isn't* our baseline-module-jvm-args plugin added an `--add-exports` for compilation,
                        // we'd correctly remove `--release`. However, the lazy `CommandLinkArgumentProviders` that
                        // make up the `options.allCompilerArgs` may do resolution (eg BaselineImmutables does so)
                        // and other complex actions. Since Gradle do not provide us with a `Provider` for these
                        // compiler args, everything goes askew and Gradle ends up complaining about "not having
                        // the project state lock". So instead of doing the completely correct general approach, we
                        // assume that people will only use our baseline-module-jvm-args plugin to add `--add-exports`,
                        // and look directly at its extension to see if any exports/opens are added. This does mean
                        // though if `--add-exports` are added another way, `--release` will remain and the compiler
                        // will complain:
                        //    error: exporting a package from system module jdk.compiler is not allowed with --release
                        // In this situation, the user will need to manually unset the release property in
                        // the buildscript: `tasks.withType(JavaCompile).configureEach { options.release.unset() }`
                        BaselineModuleJvmArgsExtension moduleJvmArgs =
                                project.getExtensions().findByType(BaselineModuleJvmArgsExtension.class);

                        if (moduleJvmArgs == null) {
                            return targetValue;
                        }

                        boolean anyExports = !Sets.union(
                                        moduleJvmArgs.exports().get(),
                                        moduleJvmArgs.opens().get())
                                .isEmpty();

                        if (anyExports) {
                            return null;
                        }

                        return targetValue;
                    }));
        });

        // Unfortunately, Gradle does not provide a Property based API for source and target compatibility,
        // so we are forced to use afterEvaluate to set them.
        // We always set `--source` and `--target`, even though `--release XX` implies `--source XX` and `--target XX`.
        // There's no harm in doing this, and avoids more state inspection of baseline-module-jvm-args.
        project.afterEvaluate(_ignored -> {
            project.getTasks().withType(JavaCompile.class).configureEach(javaCompileTask -> {
                String targetString = target.get().toString();
                javaCompileTask.setSourceCompatibility(targetString);
                javaCompileTask.setTargetCompatibility(targetString);
            });
        });

        project.getTasks().withType(Javadoc.class).configureEach(javadocTask -> {
            setJavaDocTool(javadocTask, rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target);

            // javadocTask doesn't allow us to add a CommandLineArgumentProvider, so we do it just in time
            javadocTask.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    CoreJavadocOptions options = (CoreJavadocOptions) ((Javadoc) task).getOptions();
                    if (target.get().enablePreview()) {
                        // yes, javadoc truly takes a single-dash where everyone else takes a double dash
                        options.addBooleanOption("-enable-preview", true);
                        options.setSource(target.get().javaLanguageVersion().toString());
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
            // sourceCompatibility/targetCompatibility/getOptions().setRelease(...) do nothing for
            // Groovy (poor API sharing of JavaCompile's CompileOptions with GroovyCompile). In order
            // to output classfiles with bytecode version of $target, we have to use a $target JDK to
            // run Groovy (in contrast to Java's use of `--release`).
            groovyCompileTask
                    .getJavaLauncher()
                    .set(getJavaLauncher(
                            rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target));

            groovyCompileTask
                    .getOptions()
                    .getCompilerArgumentProviders()
                    .add(new EnablePreviewArgumentProvider(target));
        });

        project.getTasks().withType(ScalaCompile.class).configureEach(scalaCompileTask -> {
            scalaCompileTask
                    .getJavaLauncher()
                    .set(getJavaLauncher(
                            rootExtension, baselineConfiguredJavaToolchains, javaToolchainService, target));

            scalaCompileTask.getOptions().getCompilerArgumentProviders().add(new EnablePreviewArgumentProvider(target));
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
            Provider<JavaLanguageVersion> javaCompilerVersion) {
        if (rootExtension.getSetupJdkToolchains().get()) {
            log.debug("Using baselineConfiguredJavaToolchains to configure the javaCompileTask");
            javaCompileTask
                    .getJavaCompiler()
                    .set(baselineConfiguredJavaToolchains
                            .forVersion(javaCompilerVersion.map(ChosenJavaVersion::of))
                            .flatMap(BaselineJavaToolchain::javaCompiler));
            return;
        }
        log.debug("Using detected javaToolchains to configure the javaCompileTask");
        javaCompileTask.getJavaCompiler().set(javaToolchainService.compilerFor(spec -> spec.getLanguageVersion()
                .set(javaCompilerVersion)));
    }

    private static void setJavaDocTool(
            Javadoc javadocTask,
            BaselineJavaVersionsExtension rootExtension,
            JavaToolchains baselineConfiguredJavaToolchains,
            JavaToolchainService javaToolchainService,
            Provider<ChosenJavaVersion> version) {
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
            Provider<ChosenJavaVersion> version) {
        if (rootExtension.getSetupJdkToolchains().get()) {
            log.debug("Using baselineConfiguredJavaToolchains to configure JavaLauncher");
            return baselineConfiguredJavaToolchains.forVersion(version).flatMap(BaselineJavaToolchain::javaLauncher);
        }
        log.debug("Using detected javaToolchains to configure JavaLauncher");
        return javaToolchainService.launcherFor(
                spec -> spec.getLanguageVersion().set(version.map(ChosenJavaVersion::javaLanguageVersion)));
    }

    public abstract static class CheckJavaVersionsTask extends DefaultTask {
        @Inject
        public CheckJavaVersionsTask() {
            setGroup("Verification");
            setDescription("Ensures configured java versions are compatible: "
                    + "The runtime version must be greater than or equal to the target version.");
            getProjectDisplayName().set(getProject().getDisplayName());
        }

        @Input
        public abstract Property<JavaLanguageVersion> getJavaCompilerVersion();

        @Input
        public abstract Property<ChosenJavaVersion> getTargetVersion();

        @Input
        public abstract Property<ChosenJavaVersion> getRuntimeVersion();

        @Console
        public abstract Property<String> getProjectDisplayName();

        @TaskAction
        public final void checkJavaVersions() {
            JavaLanguageVersion javaCompiler = getJavaCompilerVersion().get();
            ChosenJavaVersion target = getTargetVersion().get();
            ChosenJavaVersion runtime = getRuntimeVersion().get();

            getLogger()
                    .debug(
                            "BaselineJavaVersion configured {} with javaCompiler version {}, target version {} and"
                                    + " runtime version {}",
                            getProjectDisplayName().get(),
                            javaCompiler,
                            target,
                            runtime);

            if (target.enablePreview() && !target.equals(runtime)) {
                throw new GradleException(String.format(
                        "Runtime Java version (%s) must be exactly the same as the compilation target (%s) in %s, "
                                + "because --enable-preview is enabled. Otherwise Java will fail to start. See "
                                + "https://openjdk.org/jeps/12.",
                        runtime, target, getProjectDisplayName().get()));
            }

            if (target.javaLanguageVersion().asInt()
                    > runtime.javaLanguageVersion().asInt()) {
                throw new GradleException(String.format(
                        "The requested compilation target Java version (%s) must not "
                                + "exceed the requested runtime Java version (%s) in %s",
                        target, runtime, getProjectDisplayName().get()));
            }

            if (target.javaLanguageVersion().asInt() > javaCompiler.asInt()) {
                throw new GradleException(String.format(
                        "The requested compilation target Java version (%s) must not "
                                + "exceed the javaCompiler Java version (%s) in %s",
                        target, javaCompiler, getProjectDisplayName().get()));
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
