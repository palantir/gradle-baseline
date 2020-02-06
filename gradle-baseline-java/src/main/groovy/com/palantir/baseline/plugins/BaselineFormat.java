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
import com.google.common.base.Splitter;
import com.google.common.collect.Streams;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private static final String PJF_PLUGIN = "com.palantir.java-format";

    @Override
    public void apply(Project project) {
        this.project = project;

        project.getPluginManager().apply("com.diffplug.gradle.spotless");

        SpotlessExtension spotlessExtension = project.getExtensions().getByType(SpotlessExtension.class);
        // Keep spotless from eagerly configuring all other tasks.  We do the same thing as the enforceCheck
        // property below by making the check task depend on spotlessCheck.
        // See  https://github.com/diffplug/spotless/issues/444
        spotlessExtension.setEnforceCheck(false);

        // Allow disabling copyright for tests
        if (!"false".equals(project.findProperty("com.palantir.baseline-format.copyright"))) {
            configureCopyrightStep(project, spotlessExtension);
        }

        // necessary because SpotlessPlugin creates tasks in an afterEvaluate block
        TaskProvider<Task> formatTask = project.getTasks().register("format", task -> {
            task.setGroup("Formatting");
        });
        project.afterEvaluate(p -> {
            Task spotlessApply = project.getTasks().getByName("spotlessApply");
            formatTask.configure(t -> {
                t.dependsOn(spotlessApply);
            });

            // re-enable spotless checking, but lazily so it doesn't eagerly configure everything else
            project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(t -> {
                t.dependsOn(project.getTasks().named("spotlessCheck"));
            });
        });

        project.getPluginManager().withPlugin("java", plugin -> {
            configureSpotlessJava(project, spotlessExtension);
        });
    }

    private void configureCopyrightStep(Project project, SpotlessExtension spotlessExtension) {
        File copyrightDir = project.getRootProject().file(getConfigDir() + "/copyright");
        Path copyrightFile = Arrays.stream(Preconditions.checkNotNull(copyrightDir.list()))
                .sorted()
                .findFirst()
                .map(name -> copyrightDir.toPath().resolve(name))
                .orElseThrow(() -> new RuntimeException("Expected to find a copyright file inside " + copyrightDir));
        String copyrightContents;
        try {
            copyrightContents = new String(Files.readAllBytes(copyrightFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read copyright file " + copyrightFile, e);
        }
        // Spotless expects '$YEAR' but our current patterns use ${today.year}
        String copyright = copyrightContents
                .replaceAll(Pattern.quote("${today.year}"), "\\$YEAR")
                .trim();
        // Spotless expects the literal header so we have to add the Java comment guard and prefixes
        String copyrightComment = Streams.concat(
                        Stream.of("/*"),
                        Streams.stream(Splitter.on('\n').split(copyright)).map(line -> " *" + " " + line),
                        Stream.of(" */"))
                .collect(Collectors.joining("\n"));

        // Spotless will consider the license header to be the file prefix up to the first line starting with delimiter
        String delimiter = "(?! \\*|/\\*| \\*/)";
        spotlessExtension.java(java -> java.licenseHeader(copyrightComment, delimiter));

        // This is tricky as configuring this naively yields the following error:
        // > You must apply the groovy plugin before the spotless plugin if you are using the groovy extension.
        project.getPluginManager().withPlugin("groovy", groovyPlugin -> {
            spotlessExtension.groovy(groovy -> groovy.licenseHeader(copyrightComment, delimiter));
        });
    }

    private static void configureSpotlessJava(Project project, SpotlessExtension spotlessExtension) {
        if (palantirJavaFormatterState(project) == FormatterState.ON) {
            project.getPlugins().apply(PJF_PLUGIN); // provides the formatDiff task
        }

        if (eclipseFormattingEnabled(project)) {
            project.getPluginManager().withPlugin(PJF_PLUGIN, plugin -> {
                throw new GradleException(
                        "Can't use both eclipse and palantir-java-format at the same time, please delete one of "
                                + ECLIPSE_FORMATTING
                                + " or "
                                + PJF_PROPERTY
                                + " from your gradle.properties");
            });
        }

        Path eclipseXml = eclipseConfigFile(project);
        spotlessExtension.java(java -> {
            // Configure a lazy FileCollection then pass it as the target
            ConfigurableFileCollection allJavaFiles = project.files();
            project.getConvention()
                    .getPlugin(JavaPluginConvention.class)
                    .getSourceSets()
                    .all(sourceSet -> allJavaFiles.from(sourceSet.getAllJava().filter(file ->
                            !file.toString().contains(GENERATED_MARKER))));

            java.target(allJavaFiles);
            java.removeUnusedImports();
            // use empty string to specify one group for all non-static imports
            java.importOrder("");

            if (eclipseFormattingEnabled(project)) {
                java.eclipse().configFile(project.file(eclipseXml.toString()));
            }

            java.trimTrailingWhitespace();
        });

        project.afterEvaluate(p -> {
            Task spotlessJava = project.getTasks().getByName("spotlessJava");
            if (eclipseFormattingEnabled(project) && !Files.exists(eclipseXml)) {
                spotlessJava.dependsOn(":baselineUpdateConfig");
            }

            project.getTasks().withType(JavaCompile.class).configureEach(spotlessJava::mustRunAfter);
        });
    }

    static boolean eclipseFormattingEnabled(Project project) {
        return project.hasProperty(ECLIPSE_FORMATTING);
    }

    static FormatterState palantirJavaFormatterState(Project project) {
        if (!project.hasProperty(PJF_PROPERTY)) {
            return FormatterState.OFF;
        }
        Object propertyValue = project.property(PJF_PROPERTY);
        if ("started".equals(propertyValue)) {
            return FormatterState.STARTED_CONVERTING;
        }
        if ("true".equals(propertyValue) || "".equals(propertyValue)) {
            return FormatterState.ON;
        }
        throw new RuntimeException(String.format(
                "Unexpected value for %s: %s.%nExpected one of [started, true].", PJF_PROPERTY, propertyValue));
    }

    enum FormatterState {
        OFF,
        STARTED_CONVERTING,
        ON
    }

    static Path eclipseConfigFile(Project project) {
        return project.getRootDir().toPath().resolve(".baseline/spotless/eclipse.xml");
    }
}
