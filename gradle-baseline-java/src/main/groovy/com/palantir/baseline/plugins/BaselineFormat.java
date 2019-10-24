/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.google.common.base.Preconditions;
import com.palantir.baseline.plugins.format.PalantirJavaFormatStep;
import com.palantir.javaformat.gradle.JavaFormatExtension;
import com.palantir.javaformat.gradle.PalantirJavaFormatIdeaPlugin;
import com.palantir.javaformat.gradle.PalantirJavaFormatPlugin;
import com.palantir.javaformat.gradle.PalantirJavaFormatProviderPlugin;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

class BaselineFormat extends AbstractBaselinePlugin {

    // TODO(dfox): remove this feature flag when we've refined the eclipse.xml sufficiently
    private static final String ECLIPSE_FORMATTING = "com.palantir.baseline-format.eclipse";
    private static final String PJF_PROPERTY = "com.palantir.baseline-format.palantir-java-format";
    private static final String GENERATED_MARKER = File.separator + "generated";

    @Override
    public void apply(Project project) {
        this.project = project;

        if (project == project.getRootProject()) {
            if (BaselineFormat.palantirJavaFormatterEnabled(project)) {
                project.getPluginManager().apply(PalantirJavaFormatIdeaPlugin.class);
            }
        }

        project.getPluginManager().withPlugin("java", plugin -> {
            if (palantirJavaFormatterEnabled(project)) {
                project.getPlugins().apply(PalantirJavaFormatPlugin.class); // provides the formatDiff task
            }
        });

        project.getPluginManager().withPlugin("java", plugin -> {
            project.getPluginManager().apply("com.diffplug.gradle.spotless");
            Path eclipseXml = eclipseConfigFile(project);

            SpotlessExtension spotlessExtension = project.getExtensions().getByType(SpotlessExtension.class);
            spotlessExtension.java(java -> {
                // Configure a lazy FileCollection then pass it as the target
                ConfigurableFileCollection allJavaFiles = project.files();
                project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(
                        sourceSet -> allJavaFiles.from(
                                sourceSet.getAllJava().filter(file -> !file.toString().contains(GENERATED_MARKER))));

                java.target(allJavaFiles);
                java.removeUnusedImports();
                // use empty string to specify one group for all non-static imports
                java.importOrder("");

                if (eclipseFormattingEnabled(project) && palantirJavaFormatterEnabled(project)) {
                    throw new GradleException(
                            "Can't use both eclipse and palantir-java-format at the same time, please delete one of "
                                    + ECLIPSE_FORMATTING
                                    + " or "
                                    + PJF_PROPERTY
                                    + " from your gradle.properties");
                }

                if (eclipseFormattingEnabled(project)) {
                    java.eclipse().configFile(project.file(eclipseXml.toString()));
                }

                if (palantirJavaFormatterEnabled(project)) {
                    Preconditions.checkState(
                            project.getRootProject().getPlugins().hasPlugin(PalantirJavaFormatProviderPlugin.class),
                            "Must apply `com.palantir.baseline-format` to root project when setting '%s'",
                            PJF_PROPERTY);
                    java.addStep(PalantirJavaFormatStep.create(
                            project.getRootProject().getConfigurations().getByName("palantirJavaFormat"),
                            project.getRootProject().getExtensions().getByType(JavaFormatExtension.class)));
                }

                java.trimTrailingWhitespace();
            });

            // Keep spotless from eagerly configuring all other tasks.  We do the same thing as the enforceCheck
            // property below by making the check task depend on spotlessCheck.
            // See  https://github.com/diffplug/spotless/issues/444
            spotlessExtension.setEnforceCheck(false);

            // necessary because SpotlessPlugin creates tasks in an afterEvaluate block
            TaskProvider<Task> formatTask = project.getTasks().register("format", task -> {
                task.setGroup("Formatting");
            });
            project.afterEvaluate(p -> {
                Task spotlessJava = project.getTasks().getByName("spotlessJava");
                Task spotlessApply = project.getTasks().getByName("spotlessApply");
                if (eclipseFormattingEnabled(project) && !Files.exists(eclipseXml)) {
                    spotlessJava.dependsOn(":baselineUpdateConfig");
                }
                formatTask.configure(t -> {
                    t.dependsOn(spotlessApply);
                });
                project.getTasks().withType(JavaCompile.class).configureEach(spotlessJava::mustRunAfter);

                // re-enable spotless checking, but lazily so it doesn't eagerly configure everything else
                project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(t -> {
                    t.dependsOn(project.getTasks().named("spotlessCheck"));
                });
            });
        });
    }

    static boolean eclipseFormattingEnabled(Project project) {
        return project.hasProperty(ECLIPSE_FORMATTING);
    }

    static boolean palantirJavaFormatterEnabled(Project project) {
        return project.hasProperty(PJF_PROPERTY);
    }

    static Path eclipseConfigFile(Project project) {
        return project.getRootDir().toPath().resolve(".baseline/spotless/eclipse.xml");
    }
}
