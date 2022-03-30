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
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.provider.SetProperty;
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
public final class BaselineModuleJvmArgs implements Plugin<Project> {

    private static final String EXTENSION_NAME = "moduleJvmArgs";
    private static final String ADD_EXPORTS_ATTRIBUTE = "Add-Exports";
    private static final String ADD_OPENS_ATTRIBUTE = "Add-Opens";

    private static final Splitter ENTRY_SPLITTER =
            Splitter.on(' ').trimResults().omitEmptyStrings();

    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            BaselineModuleJvmArgsExtension extension =
                    project.getExtensions().create(EXTENSION_NAME, BaselineModuleJvmArgsExtension.class, project);

            // javac isn't provided `--add-exports` args for the time being due to
            // https://github.com/gradle/gradle/issues/18824
            // However, we set sourceCompatibility in BaselineJavaVersion to opt out of the '--release' flag.
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                JavaCompile javaCompile = project.getTasks()
                        .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                        .get();
                javaCompile
                        .getOptions()
                        .getCompilerArgumentProviders()
                        // Use an anonymous class because tasks with lambda inputs cannot be cached
                        .add(new CommandLineArgumentProvider() {
                            @Override
                            public Iterable<String> asArguments() {
                                // The '--release' flag is set when BaselineJavaVersion is not used.
                                if (!project.getPlugins().hasPlugin(BaselineJavaVersion.class)) {
                                    project.getLogger()
                                            .debug(
                                                    "BaselineModuleJvmArgs not applying args to compilation task "
                                                            + "{} on {} due to lack of BaselineJavaVersion",
                                                    javaCompile.getName(),
                                                    project);
                                    return ImmutableList.of();
                                }
                                // Annotation processors are executed at compile time
                                ImmutableList<String> arguments =
                                        collectAnnotationProcessorArgs(project, extension, sourceSet);
                                project.getLogger()
                                        .debug(
                                                "BaselineModuleJvmArgs compiling {} on {} with exports: {}",
                                                javaCompile.getName(),
                                                project,
                                                arguments);
                                return arguments;
                            }
                        });

                Task maybeJavadocTask = project.getTasks().findByName(sourceSet.getJavadocTaskName());
                if (maybeJavadocTask instanceof Javadoc) {
                    maybeJavadocTask.doFirst(new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            // The '--release' flag is set when BaselineJavaVersion is not used.
                            if (!project.getPlugins().hasPlugin(BaselineJavaVersion.class)) {
                                project.getLogger()
                                        .debug(
                                                "BaselineModuleJvmArgs not applying args to compilation task "
                                                        + "{} on {} due to lack of BaselineJavaVersion",
                                                task.getName(),
                                                project);
                                return;
                            }

                            Javadoc javadoc = (Javadoc) task;

                            MinimalJavadocOptions options = javadoc.getOptions();
                            if (options instanceof CoreJavadocOptions) {
                                CoreJavadocOptions coreOptions = (CoreJavadocOptions) options;
                                ImmutableList<JarManifestModuleInfo> info = collectClasspathInfo(project, sourceSet);
                                List<String> exportValues = Stream.concat(
                                                extension.exports().get().stream(),
                                                info.stream().flatMap(item -> item.exports().stream()))
                                        .distinct()
                                        .sorted()
                                        .map(item -> item + "=ALL-UNNAMED")
                                        .collect(ImmutableList.toImmutableList());
                                List<String> opens = Stream.concat(
                                                extension.opens().get().stream(),
                                                info.stream().flatMap(item -> item.opens().stream()))
                                        .distinct()
                                        .sorted()
                                        .map(item -> item + "=ALL-UNNAMED")
                                        .collect(ImmutableList.toImmutableList());
                                project.getLogger()
                                        .debug(
                                                "BaselineModuleJvmArgs building {} on {} "
                                                        + "with exports: {} and opens: {}",
                                                javadoc.getName(),
                                                project,
                                                exportValues,
                                                opens);
                                if (!exportValues.isEmpty()) {
                                    coreOptions
                                            // options are automatically prefixed with '-' internally
                                            .addMultilineStringsOption("-add-exports")
                                            .setValue(exportValues);
                                }
                                if (!opens.isEmpty()) {
                                    coreOptions
                                            // options are automatically prefixed with '-' internally
                                            .addMultilineStringsOption("-add-opens")
                                            .setValue(opens);
                                }
                            } else {
                                project.getLogger()
                                        .error(
                                                "MinimalJavadocOptions implementation was "
                                                        + "not CoreJavadocOptions, rather '{}'",
                                                options.getClass().getName());
                            }
                        }
                    });
                }
            });

            project.getTasks().withType(Test.class, new Action<Test>() {

                @Override
                public void execute(Test test) {
                    test.getJvmArgumentProviders().add(new CommandLineArgumentProvider() {

                        @Override
                        public Iterable<String> asArguments() {
                            ImmutableList<String> arguments =
                                    collectClasspathArgs(project, extension, test.getClasspath());
                            project.getLogger()
                                    .debug(
                                            "BaselineModuleJvmArgs executing {} on {} with exports: {}",
                                            test.getName(),
                                            project,
                                            arguments);
                            return arguments;
                        }
                    });
                }
            });

            project.getTasks().withType(JavaExec.class, new Action<JavaExec>() {

                @Override
                public void execute(JavaExec javaExec) {
                    javaExec.getJvmArgumentProviders().add(new CommandLineArgumentProvider() {

                        @Override
                        public Iterable<String> asArguments() {
                            ImmutableList<String> arguments =
                                    collectClasspathArgs(project, extension, javaExec.getClasspath());
                            project.getLogger()
                                    .debug(
                                            "BaselineModuleJvmArgs executing {} on {} with exports: {}",
                                            javaExec.getName(),
                                            project,
                                            arguments);
                            return arguments;
                        }
                    });
                }
            });

            project.getTasks().withType(Jar.class, new Action<Jar>() {
                @Override
                public void execute(Jar jar) {
                    jar.doFirst(new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            jar.manifest(new Action<Manifest>() {
                                @Override
                                public void execute(Manifest manifest) {
                                    addManifestAttribute(jar, manifest, ADD_EXPORTS_ATTRIBUTE, extension.exports());
                                    addManifestAttribute(jar, manifest, ADD_OPENS_ATTRIBUTE, extension.opens());
                                }
                            });
                        }
                    });
                }
            });
        });
    }

    private static void addManifestAttribute(
            Jar jarTask, Manifest manifest, String attributeName, SetProperty<String> valueProperty) {
        Project project = jarTask.getProject();
        // Only locally defined values are applied to jars
        Set<String> values = valueProperty.get();
        if (!values.isEmpty()) {
            project.getLogger()
                    .debug(
                            "BaselineModuleJvmArgs adding {} attribute to {} in {}: {}",
                            attributeName,
                            jarTask.getName(),
                            project,
                            values);
            manifest.attributes(ImmutableMap.of(attributeName, String.join(" ", values)));
        } else {
            project.getLogger()
                    .debug(
                            "BaselineModuleJvmArgs not adding {} attribute to {} in {}",
                            attributeName,
                            jarTask.getName(),
                            project);
        }
    }

    private static ImmutableList<String> collectAnnotationProcessorArgs(
            Project project, BaselineModuleJvmArgsExtension extension, SourceSet sourceSet) {
        return collectClasspathArgs(
                project,
                extension,
                project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName()));
    }

    private static ImmutableList<String> collectClasspathArgs(
            Project project, BaselineModuleJvmArgsExtension extension, FileCollection classpath) {
        ImmutableList<JarManifestModuleInfo> classpathInfo = collectClasspathInfo(project, classpath);
        Stream<String> exports = Stream.concat(
                        extension.exports().get().stream(),
                        classpathInfo.stream().flatMap(info -> info.exports().stream()))
                .distinct()
                .sorted()
                .flatMap(BaselineModuleJvmArgs::addExportArg);
        Stream<String> opens = Stream.concat(
                        extension.opens().get().stream(), classpathInfo.stream().flatMap(info -> info.opens().stream()))
                .distinct()
                .sorted()
                .flatMap(BaselineModuleJvmArgs::addOpensArg);
        return Stream.concat(exports, opens).collect(ImmutableList.toImmutableList());
    }

    private static ImmutableList<JarManifestModuleInfo> collectClasspathInfo(Project project, SourceSet sourceSet) {
        return collectClasspathInfo(
                project, project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName()));
    }

    private static ImmutableList<JarManifestModuleInfo> collectClasspathInfo(
            Project project, FileCollection classpath) {
        return classpath.getFiles().stream()
                .map(file -> {
                    try {
                        if (file.getName().endsWith(".jar") && file.isFile()) {
                            try (JarFile jar = new JarFile(file)) {
                                java.util.jar.Manifest maybeJarManifest = jar.getManifest();
                                Optional<JarManifestModuleInfo> parsedModuleInfo = parseModuleInfo(maybeJarManifest);
                                project.getLogger()
                                        .debug("Jar '{}' produced manifest info: {}", file, parsedModuleInfo);
                                return parsedModuleInfo.orElse(null);
                            }
                        }
                        return null;
                    } catch (IOException e) {
                        project.getLogger().warn("Failed to check jar {} for manifest attributes", file, e);
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

    private static Stream<String> addExportArg(String modulePackagePair) {
        return Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED");
    }

    private static Stream<String> addOpensArg(String modulePackagePair) {
        return Stream.of("--add-opens", modulePackagePair + "=ALL-UNNAMED");
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
}
