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
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
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
                    .add(ModuleJvmArgsArgumentProvider.forRuntimeUsingExtensionWithClasspath(test, test::getClasspath));
            setTaskInputsFromExtension(test, extension);
        });

        project.getTasks().withType(JavaExec.class).configureEach(javaExec -> {
            javaExec.getJvmArgumentProviders()
                    .add(ModuleJvmArgsArgumentProvider.forRuntimeUsingExtensionWithClasspath(
                            javaExec, javaExec::getClasspath));
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
        // There is more info about the plugin in its implementation class.
        // The gist is we change the compiler internals using reflection to allow the `--release`
        // and `--add-exports` args to be used together with compiler errors.

        // We always apply the plugin even if it's not necessary for two reasons:
        //   1. There's no way to lazily add an arg based on the values of other args without forcing
        //      the arg values, which might be too early.
        //   2. I think it's better to always apply the plugin, so when it starts going wrong on a JDK
        //      update, it fails very obviously everywhere and forces us to fix it rather than silently
        //      failing on the low percentage of repos that add exports/opens.

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
                    configureJavaCompile(sourceSet, extension, javaCompile);
                });

        configureJavadoc(project, sourceSet, extension);
    }

    private static void configureJavaCompile(
            SourceSet sourceSet, BaselineModuleJvmArgsExtension extension, JavaCompile javaCompile) {

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

        // For the fork options, we want just the --add-exports/--add-opens from the annotationProcessor
        // classpath. These are to enable compiler plugins like errorprone and other code that runs inside
        // the compiler to run in a process with the correct jvm args. We explicitly *do not want* the
        // exports/opens from the extension; these are for the code actually being compiled, not for
        // compiler plugins!
        javaCompile
                .getOptions()
                .getForkOptions()
                .getJvmArgumentProviders()
                .add(ModuleJvmArgsArgumentProvider.forRuntime(javaCompile)
                        .withClasspath(sourceSet::getAnnotationProcessorPath));

        // For the compiler args, we do not want any --add-exports/--add-opens:
        //   1. From the annotationProcessor classpath. These are for *compiler plugins* like errorprone,
        //      not for the code actually being compiled.
        //   2. From the dependencies of the code being compiled - these have already been compiled and
        //      we don't need to have the compiler --add-exports for them
        // We just need the exports/opens from the extension. --add-opens does nothing during compilation
        // so the argument provider below will change them to --add-exports.
        javaCompile
                .getOptions()
                .getCompilerArgumentProviders()
                .add(ModuleJvmArgsArgumentProvider.forCompilation(javaCompile).usingExtensionFor(javaCompile));

        setTaskInputsFromExtension(javaCompile, extension);
    }

    private void configureJavadoc(Project project, SourceSet sourceSet, BaselineModuleJvmArgsExtension extension) {
        if (!project.getTasks().getNames().contains(sourceSet.getJavadocTaskName())) {
            // If there's no javadoc we can't configure it
            return;
        }

        project.getTasks().named(sourceSet.getJavadocTaskName(), Javadoc.class, javadoc -> {
            // Javadoc task is horrible. There's no lazy properties/CommandLineArgumentProviders, so we have to
            // resort to this fresh hell of changing its configuration in a task action before it runs. Once
            // Gradle have made this lazy, this should be rewritten.
            javadoc.doFirst(new Action<Task>() {
                @Override
                public void execute(Task _task) {
                    MinimalJavadocOptions options = javadoc.getOptions();
                    if (!(options instanceof CoreJavadocOptions coreOptions)) {
                        log.error(
                                "MinimalJavadocOptions implementation on '{}' was not CoreJavadocOptions, rather '{}'",
                                javadoc.getPath(),
                                options.getClass().getName());
                        return;
                    }

                    Iterable<String> addExportsAndOpensArgs = ModuleJvmArgsArgumentProvider.forCompilation(javadoc)
                            // Technically this will have `--add-opens` which are not used in compilation
                            // (they need to changed to --add-exports), but we're going to strip out
                            // `--add-opens`/`-add-exports` in the next step anyway due to Javadoc
                            // task's weird `addMultilineStringsOption` method, so they will all
                            // effectively become --add-exports
                            .withClasspath(sourceSet::getAnnotationProcessorPath)
                            .asArguments();

                    List<String> exportValues = StreamSupport.stream(addExportsAndOpensArgs.spliterator(), false)
                            .filter(option -> !option.startsWith("--add"))
                            .distinct()
                            .sorted()
                            .toList();

                    log.debug("BaselineModuleJvmArgs building {} with exports: {}", javadoc.getPath(), exportValues);
                    if (!exportValues.isEmpty()) {
                        coreOptions
                                // options are automatically prefixed with '-' internally
                                .addMultilineStringsOption("-add-exports")
                                .setValue(exportValues);
                    }
                }
            });

            setTaskInputsFromExtension(javadoc, extension);
        });
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

    private static List<JarManifestModuleInfo> collectClasspathInfo(FileCollection classpath) {
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
        public abstract Property<String> getTaskPath();

        @Internal
        public abstract Property<Boolean> getForCompilation();

        @Inject
        protected abstract ProviderFactory getProviderFactory();

        @Override
        public final Iterable<String> asArguments() {
            List<String> exportsArgs = getExports().get().stream()
                    .distinct()
                    .sorted()
                    .flatMap(ModuleJvmArgsArgumentProvider::addExportArg).toList();

            List<String> opensArgs =
                    getOpens().get().stream().distinct().sorted().flatMap(ModuleJvmArgsArgumentProvider::addOpensArg).toList();

            List<String> args = Stream.concat(exportsArgs, getForCompilation().get() ? opensArgs).toList();

            log.debug(
                    "BaselineModuleJvmArgs configuring {} with exports: {}",
                    getTaskPath().get(),
                    args);

            return args;
        }

        private static Stream<String> addExportArg(String modulePackagePair) {
            return Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED");
        }

        private static Stream<String> addOpensArg(String modulePackagePair) {
            return Stream.of("--add-opens", modulePackagePair + "=ALL-UNNAMED");
        }

        public static ModuleJvmArgsArgumentProvider forRuntimeUsingExtensionWithClasspath(
                Task task, Callable<FileCollection> classpathCallable) {
            return forRuntime(task).usingExtensionFor(task).withClasspath(classpathCallable);
        }

        public static ModuleJvmArgsArgumentProvider forRuntime(Task task) {
            ModuleJvmArgsArgumentProvider argumentProvider = create(task);
            argumentProvider.getForCompilation().set(false);
            return argumentProvider;
        }

        public static ModuleJvmArgsArgumentProvider forCompilation(Task task) {
            ModuleJvmArgsArgumentProvider argumentProvider = create(task);
            argumentProvider.getForCompilation().set(true);
            return argumentProvider;
        }

        private static ModuleJvmArgsArgumentProvider create(Task task) {
            ModuleJvmArgsArgumentProvider provider =
                    task.getProject().getObjects().newInstance(ModuleJvmArgsArgumentProvider.class);

            provider.getTaskPath().set(task.getPath());

            return provider;
        }

        public final ModuleJvmArgsArgumentProvider usingExtensionFor(Task task) {
            getExports().addAll(extension(task).exports());
            getOpens().addAll(extension(task).opens());

            return this;
        }

        /**
         * The `getClasspath()` methods on many task types are not as lazy as you'd hope.
         * Taking a Callable prevents the mistake of forcing the classpath too early.
         */
        public final ModuleJvmArgsArgumentProvider withClasspath(Callable<FileCollection> classpathCallable) {
            Provider<List<JarManifestModuleInfo>> jarManifestModuleInfos =
                    getProviderFactory().provider(classpathCallable).map(BaselineModuleJvmArgs::collectClasspathInfo);

            getExports().addAll(jarManifestModuleInfos.map(extract(JarManifestModuleInfo::exports)));
            getOpens().addAll(jarManifestModuleInfos.map(extract(JarManifestModuleInfo::opens)));

            return this;
        }

        private Transformer<List<String>, List<JarManifestModuleInfo>> extract(
                Function<JarManifestModuleInfo, List<String>> extractor) {
            return jarManifestModuleInfos -> jarManifestModuleInfos.stream()
                    .flatMap(info -> extractor.apply(info).stream())
                    .toList();
        }

        private static BaselineModuleJvmArgsExtension extension(Task task) {
            return task.getProject().getExtensions().getByType(BaselineModuleJvmArgsExtension.class);
        }
    }
}
