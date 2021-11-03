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
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * This plugin reuses the {@code Add-Exports} manifest entry defined in
 * <a href="https://openjdk.java.net/jeps/261">JEP-261</a> to propagate and collect required exports
 * from transitive dependencies, and applies them to compilation (for annotation processors) and
 * execution (tests, javaExec, etc) for runtime dependencies.
 */
public final class BaselineModuleJvmArgs implements Plugin<Project> {

    private static final String EXTENSION_NAME = "moduleJvmArgs";
    private static final String ADD_EXPORTS_ATTRIBUTE = "Add-Exports";

    private static final Splitter EXPORT_SPLITTER =
            Splitter.on(' ').trimResults().omitEmptyStrings();

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            BaselineModuleJvmArgsExtension extension =
                    project.getExtensions().create(EXTENSION_NAME, BaselineModuleJvmArgsExtension.class, project);

            // javac isn't provided `--add-exports` args for the time being due to
            // https://github.com/gradle/gradle/issues/18824
            if (project.hasProperty("add.exports.to.javac")) {
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
                                    // Annotation processors are executed at compile time
                                    ImmutableList<String> arguments =
                                            collectAnnotationProcessorExports(project, extension, sourceSet);
                                    project.getLogger()
                                            .debug(
                                                    "BaselineModuleJvmArgs compiling {} on {} with exports: {}",
                                                    javaCompile.getName(),
                                                    project,
                                                    arguments);
                                    return arguments;
                                }
                            });
                });
            }

            project.getTasks().withType(Test.class, new Action<Test>() {

                @Override
                public void execute(Test test) {
                    test.getJvmArgumentProviders().add(new CommandLineArgumentProvider() {

                        @Override
                        public Iterable<String> asArguments() {
                            ImmutableList<String> arguments =
                                    collectClasspathExports(project, extension, test.getClasspath());
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
                                    collectClasspathExports(project, extension, javaExec.getClasspath());
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
                                    // Only locally defined exports are applied to jars
                                    Set<String> exports = extension.getExports().get();
                                    if (!exports.isEmpty()) {
                                        project.getLogger()
                                                .debug(
                                                        "BaselineModuleJvmArgs adding "
                                                                + "manifest attributes to {} in {}: {}",
                                                        jar.getName(),
                                                        project,
                                                        exports);
                                        manifest.attributes(
                                                ImmutableMap.of(ADD_EXPORTS_ATTRIBUTE, String.join(" ", exports)));
                                    } else {
                                        project.getLogger()
                                                .debug(
                                                        "BaselineModuleJvmArgs not adding "
                                                                + "manifest attributes to {} in {}",
                                                        jar.getName(),
                                                        project);
                                    }
                                }
                            });
                        }
                    });
                }
            });
        });
    }

    private static ImmutableList<String> collectAnnotationProcessorExports(
            Project project, BaselineModuleJvmArgsExtension extension, SourceSet sourceSet) {
        return collectClasspathExports(
                project,
                extension,
                project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName()));
    }

    private static ImmutableList<String> collectClasspathExports(
            Project project, BaselineModuleJvmArgsExtension extension, FileCollection classpath) {
        return Stream.concat(
                        classpath.getFiles().stream().flatMap(file -> {
                            try {
                                if (file.getName().endsWith(".jar") && file.isFile()) {
                                    try (JarFile jar = new JarFile(file)) {
                                        String value = jar.getManifest()
                                                .getMainAttributes()
                                                .getValue(ADD_EXPORTS_ATTRIBUTE);
                                        if (Strings.isNullOrEmpty(value)) {
                                            return Stream.empty();
                                        }
                                        project.getLogger()
                                                .debug(
                                                        "Found manifest entry {}: {} in jar {}",
                                                        ADD_EXPORTS_ATTRIBUTE,
                                                        value,
                                                        file);
                                        return EXPORT_SPLITTER.splitToStream(value);
                                    }
                                }
                                return Stream.empty();
                            } catch (IOException e) {
                                project.getLogger().warn("Failed to check jar {} for manifest attributes", file, e);
                                return Stream.empty();
                            }
                        }),
                        extension.getExports().get().stream())
                .distinct()
                .sorted()
                .flatMap(modulePackagePair -> Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED"))
                .collect(ImmutableList.toImmutableList());
    }
}
