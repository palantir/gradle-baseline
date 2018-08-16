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
import java.util.List;
import java.util.stream.Collectors;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;

public final class BaselineErrorProne implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

            project.getPluginManager().apply(ErrorPronePlugin.class);
            Configuration rawErrorProneConf = project.getConfigurations().getByName("errorprone").copy();
            project.getConfigurations().getByName("errorprone")
                    .defaultDependencies(dependencies -> dependencies.add(
                            project.getDependencies().create(
                                    "com.palantir.baseline:baseline-error-prone:latest.release")));
            // Add error-prone to bootstrap classpath of javadoc task.
            // Since there's no way of appending to the classpath we need to explicitly add current bootstrap classpath.
            if (!javaConvention.getSourceCompatibility().isJava9()) {
                List<File> bootstrapClasspath = Splitter.on(File.pathSeparator)
                        .splitToList(System.getProperty("sun.boot.class.path"))
                        .stream()
                        .map(File::new)
                        .collect(Collectors.toList());
                project.getTasks().withType(JavaCompile.class)
                        .configureEach(compile -> {
                            compile.getOptions().getCompilerArgs()
                                    .addAll(ImmutableList.of("-XepDisableWarningsInGeneratedCode",
                                            "-Xep:EqualsHashCode:ERROR",
                                            "-Xep:EqualsIncompatibleType:ERROR"));
                            compile.getOptions().setBootstrapClasspath(
                                    rawErrorProneConf.plus(project.files(bootstrapClasspath)));
                        });
                project.getTasks().withType(Test.class)
                        .configureEach(test -> {
                            test.setBootstrapClasspath(rawErrorProneConf);
                            test.bootstrapClasspath(bootstrapClasspath);
                        });
                project.getTasks().withType(Javadoc.class)
                        .configureEach(javadoc -> javadoc.getOptions().setBootClasspath(bootstrapClasspath));
            }
        });
    }

}
