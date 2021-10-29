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
import com.palantir.baseline.extensions.BaselineExportsExtension;
import java.io.IOException;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
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
 * This plugin reuses the {@code Add-Exports} manifest entry to propagate and collect required exports
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
            BaselineExportsExtension extension =
                    project.getExtensions().create(EXTENSION_NAME, BaselineExportsExtension.class, project);
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
                                // Annotation processors are executed at compile time
                                return collectAnnotationProcessorExports(project, extension, sourceSet);
                            }
                        });
            });

            project.getTasks().withType(Test.class, new Action<Test>() {

                @Override
                public void execute(Test test) {
                    test.getJvmArgumentProviders().add(new CommandLineArgumentProvider() {

                        @Override
                        public Iterable<String> asArguments() {
                            ImmutableList<String> arguments = collectClasspathExports(extension, test.getClasspath());
                            project.getLogger().error("Executing tests with additional arguments: {}", arguments);
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
                            return collectClasspathExports(extension, javaExec.getClasspath());
                        }
                    });
                }
            });

            project.getTasks().withType(Jar.class, new Action<Jar>() {
                @Override
                public void execute(Jar jar) {
                    jar.manifest(new Action<Manifest>() {
                        @Override
                        public void execute(Manifest manifest) {
                            // Only locally defined exports are applied to jars
                            Set<String> exports = extension.exports().get();
                            if (!exports.isEmpty()) {
                                manifest.attributes(ImmutableMap.of(ADD_EXPORTS_ATTRIBUTE, String.join(" ", exports)));
                            }
                        }
                    });
                }
            });
        });
    }

    private static ImmutableList<String> collectAnnotationProcessorExports(
            Project project, BaselineExportsExtension extension, SourceSet sourceSet) {
        return collectClasspathExports(
                extension, project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName()));
    }

    private static ImmutableList<String> collectClasspathExports(
            BaselineExportsExtension extension, FileCollection classpath) {
        return Stream.concat(
                        classpath.getFiles().stream().flatMap(file -> {
                            try {
                                if (file.getName().endsWith(".jar") && file.isFile()) {
                                    JarFile jar = new JarFile(file);
                                    String value = jar.getManifest()
                                            .getMainAttributes()
                                            .getValue(ADD_EXPORTS_ATTRIBUTE);
                                    if (Strings.isNullOrEmpty(value)) {
                                        return Stream.empty();
                                    }
                                    return EXPORT_SPLITTER.splitToStream(value);
                                }
                                return Stream.empty();
                            } catch (IOException e) {
                                return Stream.empty();
                            }
                        }),
                        extension.exports().get().stream())
                .distinct()
                .sorted()
                .flatMap(modulePackagePair -> Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED"))
                .collect(ImmutableList.toImmutableList());
    }
}
