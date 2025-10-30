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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.baseline.extensions.BaselineModuleJvmArgsExtension;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersion;
import com.palantir.baseline.plugins.javaversions.BaselineJavaVersionExtension;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.jvm.tasks.Jar;
import org.gradle.process.CommandLineArgumentProvider;
import org.immutables.value.Value;

/**
 * This plugin reuses the {@code Add-Exports} manifest entry defined in
 * <a href="https://openjdk.java.net/jeps/261">JEP-261</a> to propagate and collect required exports
 * from transitive dependencies, and applies them to compilation (for annotation processors) and
 * execution (tests, javaExec, etc) for runtime dependencies.
 */
public abstract class BaselineModuleJvmArgs implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(BaselineModuleJvmArgs.class);

    private static final String EXTENSION_NAME = "moduleJvmArgs";
    private static final String ENABLE_PREVIEW_ATTRIBUTE = "Baseline-Enable-Preview";
    private static final String ADD_EXPORTS_ATTRIBUTE = "Add-Exports";
    private static final String ADD_OPENS_ATTRIBUTE = "Add-Opens";

    private static final Splitter ENTRY_SPLITTER =
            Splitter.on(' ').trimResults().omitEmptyStrings();

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    public final void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            applyToJavaProject(project);
        });
    }

    private void applyToJavaProject(Project project) {
        @SuppressWarnings({"for-rollout:GradleTypesAsFields", "for-rollout:NonAbstractGradleType"})
        BaselineModuleJvmArgsExtension extension =
                project.getExtensions().create(EXTENSION_NAME, BaselineModuleJvmArgsExtension.class, project);

        addReleaseAndAddExportsArgsFixingCompilerPlugin(project);

        // Derive this plugin's `enablePreview` property from BaselineJavaVersion's extension
        project.getPlugins().withType(BaselineJavaVersion.class, _unused -> {
            BaselineJavaVersionExtension javaVersionsExtension =
                    project.getExtensions().getByType(BaselineJavaVersionExtension.class);
            extension.setEnablePreview(javaVersionsExtension.runtime().map(chosenJavaVersion -> {
                return chosenJavaVersion.enablePreview()
                        ? Optional.of(chosenJavaVersion.javaLanguageVersion())
                        : Optional.empty();
            }));
        });

        project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
            configureSourceSet(project, sourceSet, extension);
        });

        project.getTasks().withType(Test.class).configureEach(test -> {
            test.getJvmArgumentProviders()
                    .add(ModuleJvmArgsArgumentProvider.fromClasspathAndExtension(test, test::getClasspath));
            setTaskInputsFromExtension(test, extension);
        });

        project.getTasks().withType(JavaExec.class).configureEach(javaExec -> {
            javaExec.getJvmArgumentProviders()
                    .add(ModuleJvmArgsArgumentProvider.fromClasspathAndExtension(javaExec, javaExec::getClasspath));
            setTaskInputsFromExtension(javaExec, extension);
        });

        project.getTasks().withType(Jar.class).configureEach(jar -> {
            String jarName = jar.getName();
            String projectPath = jar.getProject().getPath();

            jar.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    jar.manifest(new Action<Manifest>() {
                        @Override
                        public void execute(Manifest manifest) {
                            addManifestAttribute(
                                    jarName, projectPath, manifest, ADD_EXPORTS_ATTRIBUTE, extension.exports());
                            addManifestAttribute(
                                    jarName, projectPath, manifest, ADD_OPENS_ATTRIBUTE, extension.opens());
                            addManifestAttribute(
                                    jarName,
                                    projectPath,
                                    manifest,
                                    ENABLE_PREVIEW_ATTRIBUTE,
                                    extension.getEnablePreview().map(maybeVersion -> maybeVersion.stream()
                                            .map(v -> Integer.toString(v.asInt()))
                                            .collect(Collectors.toSet())));
                        }
                    });
                }
            });

            setTaskInputsFromExtension(jar, extension);
        });
    }

    private void addReleaseAndAddExportsArgsFixingCompilerPlugin(Project project) {
        String version = Optional.ofNullable(
                        (String) project.findProperty("baselineModuleJvmArgsCompilerPluginsVersion"))
                .or(() -> Optional.ofNullable(
                        BaselineErrorProne.class.getPackage().getImplementationVersion()))
                .orElseThrow(() -> new RuntimeException(
                        "baseline-module-jvm-args-compiler-plugins implementation version not found"));

        project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
            project.getDependencies()
                    .add(
                            sourceSet.getAnnotationProcessorConfigurationName(),
                            "com.palantir.baseline:baseline-module-jvm-args-compiler-plugins:" + version);

            project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, javaCompile -> {
                javaCompile.getOptions().getCompilerArgumentProviders().add(new CommandLineArgumentProvider() {
                    private static final String COMPILER_PLUGIN_NAME =
                            "AllowReleaseAndAddExportsToBeUsedTogetherByChangingCompilerInternalsUsingReflection";

                    @Override
                    public Iterable<String> asArguments() {
                        return List.of("-Xplugin:" + COMPILER_PLUGIN_NAME);
                    }
                });
            });
        });
    }

    private void configureSourceSet(Project project, SourceSet sourceSet, BaselineModuleJvmArgsExtension extension) {
        project.getTasks()
                .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                .configure(javaCompile -> {
                    configureJavaCompile(project, sourceSet, extension, javaCompile);
                });

        configureJavadoc(project, sourceSet, extension);
    }

    private static void configureJavaCompile(
            Project project, SourceSet sourceSet, BaselineModuleJvmArgsExtension extension, JavaCompile javaCompile) {

        // We will *always* fork the compiler - for both consistency and correctness:
        // Consistency: when fork=false, Gradle will sometimes still make a forked Gradle worker daemon
        //              for compilation! If the main Gradle daemon is running on the correct JDK major version,
        //              Gradle will just use the current daemon. However, if the main Gradle daemon is
        //              running with a different JDK Gradle will fork off a worker daemon to do the compilation.
        //              There are material differences in behaviour when it is forked vs not forked, so
        //              we always for consistency.
        // Correctness: If fork=false and Gradle thinks it can reuse the main Gradle daemon as it's running
        //              with the correct JDK major version, the main daemon will not necessarily have
        //              the required --add-exports/--add-opens set on the process running the compilations,
        //              *even if* we've specified them in forkOptions. Forking stops this from happening.
        javaCompile.getOptions().setFork(true);

        javaCompile
                .getOptions()
                .getForkOptions()
                .getJvmArgumentProviders()
                .add(ModuleJvmArgsArgumentProvider.fromJustClasspath(
                        javaCompile, sourceSet::getAnnotationProcessorPath));

        javaCompile
                .getOptions()
                .getCompilerArgumentProviders()
                .add(ModuleJvmArgsArgumentProvider.fromJustExtensionForCompilation(javaCompile));

        setTaskInputsFromExtension(javaCompile, extension);
    }

    private void configureJavadoc(Project project, SourceSet sourceSet, BaselineModuleJvmArgsExtension extension) {
        TaskProvider<Task> javadocTaskProvider = null;
        try {
            javadocTaskProvider = project.getTasks().named(sourceSet.getJavadocTaskName());
        } catch (UnknownTaskException e) {
            // skip
        }
        if (javadocTaskProvider != null) {
            javadocTaskProvider.configure(javadocTask -> {
                javadocTask.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        // The '--release' flag is set when BaselineJavaVersion is not used.
                        if (!project.getPlugins().hasPlugin(BaselineJavaVersion.class)) {
                            log.debug(
                                    "BaselineModuleJvmArgs not applying args to compilation task {} on {} "
                                            + "due to lack of BaselineJavaVersion",
                                    task.getName(),
                                    project.getPath());
                            return;
                        }

                        Javadoc javadoc = (Javadoc) task;

                        MinimalJavadocOptions options = javadoc.getOptions();
                        if (options instanceof CoreJavadocOptions coreOptions) {
                            ImmutableList<JarManifestModuleInfo> info = collectClasspathInfoForSourceSet(sourceSet);
                            List<String> exportValues = Stream.concat(
                                            // Compilation only supports exports, so we union with opens.
                                            Stream.concat(
                                                    extension.exports().get().stream(),
                                                    extension.opens().get().stream()),
                                            info.stream()
                                                    .flatMap(item -> Stream.concat(
                                                            item.exports().stream(), item.opens().stream())))
                                    .distinct()
                                    .sorted()
                                    .map(item -> item + "=ALL-UNNAMED")
                                    .collect(ImmutableList.toImmutableList());
                            log.debug(
                                    "BaselineModuleJvmArgs building {} on {} with exports: {}",
                                    javadoc.getName(),
                                    project.getPath(),
                                    exportValues);
                            if (!exportValues.isEmpty()) {
                                coreOptions
                                        // options are automatically prefixed with '-' internally
                                        .addMultilineStringsOption("-add-exports")
                                        .setValue(exportValues);
                            }
                        } else {
                            log.error(
                                    "MinimalJavadocOptions implementation was " + "not CoreJavadocOptions, rather '{}'",
                                    options.getClass().getName());
                        }
                    }
                });

                setTaskInputsFromExtension(javadocTask, extension);
            });
        }
    }

    private static void setTaskInputsFromExtension(Task task, BaselineModuleJvmArgsExtension extension) {
        task.getInputs().property("baseline-module-jvm-args-extension-exports", extension.exports());
        task.getInputs().property("baseline-module-jvm-args-extension-opens", extension.opens());
        task.getInputs().property("baseline-module-jvm-args-extension-enablePreview", extension.getEnablePreview());
    }

    private static void addManifestAttribute(
            String jarName,
            String projectPath,
            Manifest manifest,
            String attributeName,
            Provider<Set<String>> valueProperty) {
        Set<String> values = valueProperty.get();
        if (!values.isEmpty()) {
            log.debug(
                    "BaselineModuleJvmArgs adding {} attribute to {} in {}: {}",
                    attributeName,
                    jarName,
                    projectPath,
                    values);
            manifest.attributes(ImmutableMap.of(attributeName, String.join(" ", values)));
        } else {
            log.debug("BaselineModuleJvmArgs not adding {} attribute to {} in {}", attributeName, jarName, projectPath);
        }
    }

    private ImmutableList<JarManifestModuleInfo> collectClasspathInfoForSourceSet(SourceSet sourceSet) {
        FileCollection classpath = getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName());
        return collectClasspathInfo(classpath);
    }

    private static ImmutableList<JarManifestModuleInfo> collectClasspathInfo(FileCollection classpath) {
        return classpath.getFiles().stream()
                .map(file -> {
                    try {
                        if (file.getName().endsWith(".jar") && file.isFile()) {
                            try (JarFile jar = new JarFile(file)) {
                                java.util.jar.Manifest maybeJarManifest = jar.getManifest();
                                Optional<JarManifestModuleInfo> parsedModuleInfo = parseModuleInfo(maybeJarManifest);
                                log.debug("Jar '{}' produced manifest info: {}", file, parsedModuleInfo);
                                return parsedModuleInfo.orElse(null);
                            }
                        }
                        return null;
                    } catch (IOException e) {
                        log.warn("Failed to check jar {} for manifest attributes", file, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(ImmutableList.toImmutableList());
    }

    private static Optional<JarManifestModuleInfo> parseModuleInfo(@Nullable java.util.jar.Manifest jarManifest) {
        return Optional.ofNullable(jarManifest)
                .<JarManifestModuleInfo>map(manifest -> JarManifestModuleInfo.builder()
                        .exports(readManifestAttribute(manifest, ADD_EXPORTS_ATTRIBUTE))
                        .opens(readManifestAttribute(manifest, ADD_OPENS_ATTRIBUTE))
                        .build())
                .filter(JarManifestModuleInfo::isPresent);
    }

    private static List<String> readManifestAttribute(java.util.jar.Manifest jarManifest, String attribute) {
        return Optional.ofNullable(
                        Strings.emptyToNull(jarManifest.getMainAttributes().getValue(attribute)))
                .map(ENTRY_SPLITTER::splitToList)
                .orElseGet(ImmutableList::of);
    }

    @Value.Immutable
    interface JarManifestModuleInfo {
        ImmutableList<String> exports();

        ImmutableList<String> opens();

        default boolean isEmpty() {
            return exports().isEmpty() && opens().isEmpty();
        }

        default boolean isPresent() {
            return !isEmpty();
        }

        static Builder builder() {
            return new Builder();
        }

        class Builder extends ImmutableJarManifestModuleInfo.Builder {}
    }

    public abstract static class ModuleJvmArgsArgumentProvider implements CommandLineArgumentProvider {
        private static final Logger log = Logging.getLogger(ModuleJvmArgsArgumentProvider.class);

        @Internal
        public abstract SetProperty<String> getExports();

        @Internal
        public abstract SetProperty<String> getOpens();

        @Internal
        public abstract ConfigurableFileCollection getClasspath();

        @Internal
        public abstract Property<String> getTaskPath();

        @Inject
        protected abstract ProjectLayout getProjectLayout();

        public static CommandLineArgumentProvider fromJustClasspath(
                Task task, Callable<FileCollection> classpathCallable) {
            return create(task).configureWithClasspath(classpathCallable);
        }

        public static CommandLineArgumentProvider fromJustExtensionForCompilation(Task task) {
            ModuleJvmArgsArgumentProvider argumentProvider = create(task);
            argumentProvider.getExports().addAll(extension(task).exports());
            argumentProvider.getExports().addAll(extension(task).opens());
            return argumentProvider;
        }

        public static CommandLineArgumentProvider fromClasspathAndExtension(
                Task task, Callable<FileCollection> classpathCallable) {
            ModuleJvmArgsArgumentProvider argumentProvider = create(task).configureWithClasspath(classpathCallable);
            argumentProvider.getExports().addAll(extension(task).exports());
            argumentProvider.getOpens().addAll(extension(task).opens());
            return argumentProvider;
        }

        private static ModuleJvmArgsArgumentProvider create(Task task) {
            ModuleJvmArgsArgumentProvider provider =
                    task.getProject().getObjects().newInstance(ModuleJvmArgsArgumentProvider.class);

            provider.getTaskPath().set(task.getPath());

            return provider;
        }

        private static BaselineModuleJvmArgsExtension extension(Task task) {
            return task.getProject().getExtensions().getByType(BaselineModuleJvmArgsExtension.class);
        }

        // The `getClasspath()` methods on many task types are not as lazy as you'd hope.
        // Taking a Callable prevents the mistake of forcing the classpath too early.
        private ModuleJvmArgsArgumentProvider configureWithClasspath(Callable<FileCollection> classpathCallable) {
            getClasspath().from(getProjectLayout().files(classpathCallable));
            return this;
        }

        @Override
        public final Iterable<String> asArguments() {
            List<JarManifestModuleInfo> classpathInfo = collectClasspathInfo(getClasspath());
            Stream<String> allExports = Stream.concat(
                    getExports().get().stream(), classpathInfo.stream().flatMap(info -> info.exports().stream()));
            Stream<String> allOpens = Stream.concat(
                    getOpens().get().stream(), classpathInfo.stream().flatMap(info -> info.opens().stream()));

            List<String> args = runtimeArgs(allExports, allOpens);

            log.debug(
                    "BaselineModuleJvmArgs configuring {} with exports: {}",
                    getTaskPath().get(),
                    args);

            return args;
        }

        private static List<String> runtimeArgs(Stream<String> allExports, Stream<String> allOpens) {
            Stream<String> exportsArgs =
                    allExports.distinct().sorted().flatMap(ModuleJvmArgsArgumentProvider::addExportArg);
            Stream<String> opensArgs = allOpens.distinct().sorted().flatMap(ModuleJvmArgsArgumentProvider::addOpensArg);
            return Stream.concat(exportsArgs, opensArgs).toList();
        }

        private static Stream<String> addExportArg(String modulePackagePair) {
            return Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED");
        }

        private static Stream<String> addOpensArg(String modulePackagePair) {
            return Stream.of("--add-opens", modulePackagePair + "=ALL-UNNAMED");
        }
    }
}
