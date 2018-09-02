/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.AbstractList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.ltgt.gradle.errorprone.javacplugin.CheckSeverity;
import net.ltgt.gradle.errorprone.javacplugin.ErrorProneJavacPluginPlugin;
import net.ltgt.gradle.errorprone.javacplugin.ErrorProneOptions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;

public final class BaselineErrorProne implements Plugin<Project> {

    private static final String ERROR_PRONE_JAVAC_VERSION = "9+181-r4173-1";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            project.getPluginManager().apply(ErrorProneJavacPluginPlugin.class);
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
            String version = Optional.ofNullable(getClass().getPackage().getImplementationVersion())
                    .orElse("latest.release");
            project.getDependencies().add(
                    ErrorProneJavacPluginPlugin.CONFIGURATION_NAME,
                    "com.palantir.baseline:baseline-error-prone:" + version);


            // This plugin interferes with gradles native annotation processor path configuration so need to configure
            // it as well.
            project.getPluginManager().withPlugin("org.inferred.processors", processorsplugin ->
                    project.getConfigurations()
                            .named(ErrorProneJavacPluginPlugin.CONFIGURATION_NAME)
                            .configure(errorProneConf -> project.getConfigurations()
                                    .named("processor")
                                    .configure(conf -> conf.extendsFrom(errorProneConf))));
            project.getTasks().withType(JavaCompile.class).configureEach(javaCompile ->
                    ((ExtensionAware) javaCompile.getOptions()).getExtensions()
                            .configure(ErrorProneOptions.class, errorProneOptions -> {
                                errorProneOptions.setEnabled(true);
                                errorProneOptions.setDisableWarningsInGeneratedCode(true);
                                errorProneOptions.check("EqualsHashCode", CheckSeverity.ERROR);
                                errorProneOptions.check("EqualsIncompatibleType", CheckSeverity.ERROR);
                            }));

            // Add error-prone to bootstrap classpath of tasks performing java compilation.
            // Since there's no way of appending to the classpath we need to explicitly add current bootstrap classpath.
            if (!javaConvention.getSourceCompatibility().isJava9Compatible()) {
                project.getDependencies().add(ErrorProneJavacPluginPlugin.JAVAC_CONFIGURATION_NAME,
                        "com.google.errorprone:javac:" + ERROR_PRONE_JAVAC_VERSION);
                project.getConfigurations()
                        .named(ErrorProneJavacPluginPlugin.JAVAC_CONFIGURATION_NAME)
                        .configure(conf -> {
                            List<File> bootstrapClasspath = Splitter.on(File.pathSeparator)
                                    .splitToList(System.getProperty("sun.boot.class.path"))
                                    .stream()
                                    .map(File::new)
                                    .collect(Collectors.toList());
                            FileCollection errorProneFiles = conf.plus(project.files(bootstrapClasspath));
                            project.getTasks().withType(Test.class)
                                    .configureEach(test -> test.setBootstrapClasspath(errorProneFiles));
                            project.getTasks().withType(Javadoc.class)
                                    .configureEach(javadoc -> javadoc.getOptions()
                                            .setBootClasspath(new LazyConfigurationList(errorProneFiles)));
                        });
            }
        });
    }

    private static final class LazyConfigurationList extends AbstractList<File> {
        private final FileCollection files;
        private List<File> fileList;

        private LazyConfigurationList(FileCollection files) {
            this.files = files;
        }

        @Override
        public File get(int index) {
            if (fileList == null) {
                fileList = ImmutableList.copyOf(files.getFiles());
            }
            return fileList.get(index);
        }

        @Override
        public int size() {
            if (fileList == null) {
                fileList = ImmutableList.copyOf(files.getFiles());
            }
            return fileList.size();
        }
    }

}
