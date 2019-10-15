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
import com.diffplug.spotless.FormatterFunc;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.palantir.javaformat.java.FormatterService;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import com.palantir.javaformat.java.JavaOutput;
import com.palantir.javaformat.java.Replacement;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
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

        project.getPluginManager().withPlugin("java", plugin -> {
            project.getPluginManager().apply("com.diffplug.gradle.spotless");
            Path eclipseXml = eclipseConfigFile(project);

            SpotlessExtension spotlessExtension = project.getExtensions().getByType(SpotlessExtension.class);
            spotlessExtension.java(java -> {
                // Configure a lazy FileCollection then pass it as the target
                ConfigurableFileCollection allJavaFiles = project.files();
                project
                        .getConvention()
                        .getPlugin(JavaPluginConvention.class)
                        .getSourceSets()
                        .all(sourceSet -> allJavaFiles.from(
                                sourceSet.getAllJava().filter(file -> !file.toString().contains(GENERATED_MARKER))));

                java.target(allJavaFiles);
                java.removeUnusedImports();
                // use empty string to specify one group for all non-static imports
                java.importOrder("");

                if (eclipseFormattingEnabled(project) && palantirJavaFormatterEnabled(project)) {
                    throw new GradleException(
                            "Can't use both eclipse and palantir-java-format at the same time, please delete one of "
                                    + ECLIPSE_FORMATTING + " or " + PJF_PROPERTY + " from your gradle.properties");
                }

                if (eclipseFormattingEnabled(project)) {
                    java.eclipse().configFile(project.file(eclipseXml.toString()));
                }

                if (palantirJavaFormatterEnabled(project)) {
                    java.customLazy("palantir-java-format", PalantirJavaFormatterFunc::new);
                }

                java.trimTrailingWhitespace();
            });

            // Keep spotless from eagerly configuring all other tasks.  We do the same thing as the enforceCheck
            // property below by making the check task depend on spotlessCheck.
            // See  https://github.com/diffplug/spotless/issues/444
            spotlessExtension.setEnforceCheck(false);

            // necessary because SpotlessPlugin creates tasks in an afterEvaluate block
            TaskProvider<Task> formatTask = project.getTasks().register("format");
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

                //re-enable spotless checking, but lazily so it doesn't eagerly configure everything else
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

    private static class PalantirJavaFormatterFunc implements FormatterFunc {
        private static final JavaFormatterOptions OPTIONS =
                JavaFormatterOptions.builder().style(Style.PALANTIR).build();
        private final FormatterService formatterService =
                Iterables.getOnlyElement(ServiceLoader.load(FormatterService.class));

        @Override
        public String apply(String input) throws Exception {
            ImmutableList<Replacement> replacements = formatterService.getFormatReplacements(
                    OPTIONS, input, ImmutableList.of(Range.closedOpen(0, input.length())));
            return JavaOutput.applyReplacements(input, replacements);
        }
    }
}
