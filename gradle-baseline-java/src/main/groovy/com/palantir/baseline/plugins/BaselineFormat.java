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
import com.diffplug.spotless.FormatterStep;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
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
import org.gradle.language.base.plugins.LifecycleBasePlugin;

class BaselineFormat extends AbstractBaselinePlugin {

    // TODO(dfox): remove this feature flag when we've refined the eclipse.xml sufficiently
    private static final String ECLIPSE_FORMATTING = "com.palantir.baseline-format.eclipse";
    private static final String PJF_PROPERTY = "com.palantir.baseline-format.palantir-java-format";
    private static final String GENERATED_MARKER = File.separator + "generated";
    private static final String PJF_PLUGIN = "com.palantir.java-format";

    @Override
    public void apply(Project project) {
        this.project = project;

        project.getPluginManager().apply("com.diffplug.spotless");
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        SpotlessExtension spotlessExtension = project.getExtensions().getByType(SpotlessExtension.class);
        // Keep spotless from eagerly configuring all other tasks.  We do the same thing as the enforceCheck
        // property below by making the check task depend on spotlessCheck.
        // See  https://github.com/diffplug/spotless/issues/444
        spotlessExtension.setEnforceCheck(false);

        // Allow disabling copyright for tests
        if (!"false".equals(project.findProperty("com.palantir.baseline-format.copyright"))) {
            configureCopyrightStep(project, spotlessExtension);
        }

        if ("true".equals(project.findProperty("com.palantir.baseline-format.gradle-files"))) {
            configureBuildGradleFormatter(project, spotlessExtension);
        }

        // necessary because SpotlessPlugin creates tasks in an afterEvaluate block
        TaskProvider<Task> formatTask = project.getTasks().register("format", task -> {
            task.setGroup("Formatting");
        });
        project.afterEvaluate(p -> {
            formatTask.configure(t -> {
                t.dependsOn("spotlessApply");
            });

            // re-enable spotless checking, but lazily so it doesn't eagerly configure everything else
            project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(t -> {
                t.dependsOn(project.getTasks().named("spotlessCheck"));
            });

            // The copyright step configures itself lazily to allow for baselineUpdateConfig to potentially create the
            // right files. Therefore, also make sure that these will run in the right order.
            project.getPluginManager().withPlugin("com.palantir.baseline-config", baselineConfig -> {
                project.getTasks()
                        .matching(t -> t.getName().startsWith("spotless"))
                        .configureEach(t -> t.mustRunAfter("baselineUpdateConfig"));
            });
        });

        project.getPluginManager().withPlugin("java", plugin -> {
            configureSpotlessJava(project, spotlessExtension);
        });
    }

    private static void configureBuildGradleFormatter(Project project, SpotlessExtension spotlessExtension) {
        Path buildDir = project.getRootProject().getBuildDir().toPath();
        Path configFile = buildDir.resolve("baseline-format").resolve("greclipse.properties");

        try {
            URL url = Resources.getResource(BaselineCheckstyle.class, "/greclipse.properties");
            Preconditions.checkNotNull(url, "Unable to find resource");
            Files.createDirectories(configFile.getParent());
            // yes this will overwrite it for each subproject, maybe that's fine???
            Files.write(configFile, Resources.toByteArray(url), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy greclipse.properties resource to " + configFile, e);
        }

        spotlessExtension.groovyGradle(ext -> {
            ext.greclipse().configFile(configFile.toAbsolutePath());
        });
    }

    /**
     * Necessary in order to not fail right away if the copyright folder doesn't exist yet, because it would be created
     * by {@code baselineUpdateConfig}.
     */
    private FormatterStep createLazyLicenseHeaderStep(Project project) {
        return new LazyFormatterStep(MultiLicenseHeaderStep.name(), () -> {
            List<String> headers = computeCopyrightHeaders(project);
            return MultiLicenseHeaderStep.createFromHeaders(headers);
        });
    }

    private void configureCopyrightStep(Project project, SpotlessExtension spotlessExtension) {
        project.getPluginManager().withPlugin("java", javaPlugin -> {
            spotlessExtension.java(java -> java.addStep(createLazyLicenseHeaderStep(project)));
        });

        // This is tricky as configuring this naively yields the following error:
        // > You must apply the groovy plugin before the spotless plugin if you are using the groovy extension.
        project.getPluginManager().withPlugin("groovy", groovyPlugin -> {
            spotlessExtension.groovy(groovy -> groovy.addStep(createLazyLicenseHeaderStep(project)));
        });
    }

    /**
     * Computes all the copyright headers based on the files inside baseline's {@code copyright} directory. This list
     * is sorted lexicographically by the file names.
     */
    private List<String> computeCopyrightHeaders(Project project) {
        File copyrightDir = project.getRootProject().file(getConfigDir() + "/copyright");
        Stream<Path> files;
        try {
            files = Files.list(copyrightDir.toPath()).sorted(Comparator.comparing(Path::getFileName));
        } catch (IOException e) {
            throw new RuntimeException("Couldn't list copyright directory: " + copyrightDir);
        }

        return files.map(BaselineFormat::computeCopyrightComment).collect(Collectors.toList());
    }

    private static String computeCopyrightComment(Path copyrightFile) {
        try {
            return new String(Files.readAllBytes(copyrightFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read copyright file " + copyrightFile, e);
        }
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
                    .all(sourceSet -> allJavaFiles.from(sourceSet.getAllJava().filter(file -> !file.toString()
                            .contains(GENERATED_MARKER))));

            java.target(allJavaFiles);
            if (!project.getPluginManager().hasPlugin(PJF_PLUGIN)) {
                // The palantir-java-format plugin removes unused imports already, there's no reason to
                // rerun this step resolving google-java-format.
                java.removeUnusedImports();
            }
            // use empty string to specify one group for all non-static imports
            java.importOrder("");

            if (eclipseFormattingEnabled(project)) {
                java.eclipse().configFile(project.file(eclipseXml.toString()));
            }

            java.trimTrailingWhitespace();
        });

        project.afterEvaluate(p -> {
            if (eclipseFormattingEnabled(project) && !Files.exists(eclipseXml)) {
                TaskProvider<Task> spotlessJava = project.getTasks().named("spotlessJava");
                spotlessJava.configure(t -> t.dependsOn(":baselineUpdateConfig"));
            }

            project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
                Task spotlessJava = project.getTasks().getByName("spotlessJava");
                spotlessJava.mustRunAfter(javaCompile);
            });
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
