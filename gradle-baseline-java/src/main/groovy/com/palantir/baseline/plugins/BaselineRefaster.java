/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Plugin for compiling Refaster rules.
 */
public final class BaselineRefaster implements Plugin<Project> {

    public static final String CONFIGURATION_NAME = "refaster";
    public static final String SOURCE_SET_NAME = "refaster";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
            javaConvention.getSourceSets().create(SOURCE_SET_NAME, sourceSet -> {
                sourceSet.getJava().setSrcDirs(ImmutableList.of(String.format("src/%s/java", SOURCE_SET_NAME)));
            });

            Configuration refasterConfiguration = project.getConfigurations().create(CONFIGURATION_NAME);
            project.getDependencies().add(
                    CONFIGURATION_NAME,
                    "com.google.errorprone:error_prone_refaster:" + BaselineErrorProne.ERROR_PRONE_JAVAC_VERSION);

            // Refaster only compiles one file at a time, so create a task for each rule
            // https://github.com/google/error-prone/issues/552
            Task compileRefasterTask = project.getTasks().create("compileRefaster");

            for (File refasterRule : javaConvention.getSourceSets().getByName(SOURCE_SET_NAME).getAllJava()) {
                String refasterRuleName = refasterRule.getName().substring(refasterRule.getName().lastIndexOf('.'));
                Path outputPath = project.getBuildDir().toPath().resolve("refaster/" + refasterRuleName + ".refaster");

                project.getTasks().create("compileRefaster" + refasterRuleName, JavaCompile.class, task -> {
                    task.setSource(refasterRule);
                    // We don't need the .class output
                    task.setDestinationDir(task.getTemporaryDir());
                    task.setClasspath(refasterConfiguration);

                    // We want to use the errorprone compiler, but replace the default arguments with refaster
                    task.getOptions().getCompilerArgumentProviders().clear();
                    task.getOptions().setCompilerArgs(ImmutableList.of(
                            "-Xplugin:RefasterRuleCompiler --out " + outputPath));

                    task.getInputs().file(refasterRule);
                    task.getOutputs().file(outputPath);
                    compileRefasterTask.dependsOn(task);
                });
            }
        });
    }

}
