/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.suppressibleerrorprone;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

public final class SuppressibleErrorPronePlugin implements Plugin<Project> {
    public static final String SUPPRESS_STAGE_ONE = "errorProneSuppressStage1";
    public static final String SUPPRESS_STAGE_TWO = "errorProneSuppressStage2";

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", unused -> {
            applyToJavaProject(project);
        });
    }

    private void applyToJavaProject(Project project) {
        project.getPluginManager().apply(ErrorPronePlugin.class);

        SuppressibleErrorProneExtension extension =
                project.getExtensions().create("suppressibleErrorProne", SuppressibleErrorProneExtension.class);

        setupTransform(project);

        String version = Optional.ofNullable((String) project.findProperty("suppressibleErrorProneVersion"))
                .or(() -> Optional.ofNullable(
                        SuppressibleErrorPronePlugin.class.getPackage().getImplementationVersion()))
                .orElseThrow(
                        () -> new RuntimeException("SuppressibleErrorPronePlugin implementation version not found"));

        project.getConfigurations().named(ErrorPronePlugin.CONFIGURATION_NAME).configure(errorProneConfiguration -> {
            errorProneConfiguration
                    .getDependencies()
                    .add(project.getDependencies().create("com.palantir.baseline:suppressible-error-prone:" + version));
        });

        if (isStageTwo(project)) {
            project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
                project.getDependencies()
                        .add(
                                sourceSet.getCompileOnlyConfigurationName(),
                                "com.palantir.baseline:suppressible-error-prone-annotations:" + version);
            });
        }

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            configureJavaCompile(project, javaCompile);

            ((ExtensionAware) javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        configureErrorProneOptions(project, extension, errorProneOptions);
                    });
        });
    }

    private static void setupTransform(Project project) {
        Attribute<Boolean> suppressiblified =
                Attribute.of("com.palantir.baseline.errorprone.suppressiblified", Boolean.class);
        project.getDependencies().getAttributesSchema().attribute(suppressiblified);

        project.getDependencies()
                .getArtifactTypes()
                .getByName("jar")
                .getAttributes()
                .attribute(suppressiblified, false);

        project.getConfigurations().named("annotationProcessor").configure(errorProneConfiguration -> {
            errorProneConfiguration
                    .getDependencies()
                    .add(project.getDependencies().create("com.google.errorprone:error_prone_check_api"));
            errorProneConfiguration.getAttributes().attribute(suppressiblified, true);
        });

        project.getDependencies().registerTransform(Suppressiblify.class, spec -> {
            spec.getParameters().getCacheBust().set(UUID.randomUUID().toString());
            spec.getParameters().getSuppressionStage1().set(isStageOne(project));

            Attribute<String> artifactType = Attribute.of("artifactType", String.class);
            spec.getFrom().attribute(suppressiblified, false).attribute(artifactType, "jar");
            spec.getTo().attribute(suppressiblified, true).attribute(artifactType, "jar");
        });
    }

    private void configureJavaCompile(Project project, JavaCompile javaCompile) {
        javaCompile.getOptions().getForkOptions().getJvmArgumentProviders().add(new CommandLineArgumentProvider() {
            @Override
            public Iterable<String> asArguments() {
                return List.of("-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5005");
            }
        });

        if (isRefactoring(project) || isStageOne(project) || isStageTwo(project)) {
            // Don't attempt to cache since it won't capture the source files that might be modified
            javaCompile.getOutputs().cacheIf(t -> false);
        }
    }

    private void configureErrorProneOptions(
            Project project, SuppressibleErrorProneExtension extension, ErrorProneOptions errorProneOptions) {
        errorProneOptions.getDisableWarningsInGeneratedCode().set(true);

        if (isStageOne(project)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return List.of(
                            "-XepPatchLocation:IN_PLACE",
                            "-XepPatchChecks:",
                            "-XepOpt:" + SuppressibleErrorPronePlugin.SUPPRESS_STAGE_ONE + "=true");
                }
            });
            return;
        }

        if (isStageTwo(project)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return List.of("-XepPatchLocation:IN_PLACE", "-XepPatchChecks:SuppressWarningsCoalesce");
                }
            });
            return;
        }

        if (isRefactoring(project)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    String possibleSpecificPatchChecks = (String) project.property("errorProneApply");
                    if (!(possibleSpecificPatchChecks == null || possibleSpecificPatchChecks.isBlank())) {
                        List<String> specificPatchChecks = Arrays.stream(possibleSpecificPatchChecks.split(","))
                                .map(String::trim)
                                .filter(Predicate.not(String::isEmpty))
                                .collect(Collectors.toList());

                        return List.of(
                                "-XepPatchLocation:IN_PLACE",
                                "-XepPatchChecks:" + String.join(",", specificPatchChecks));
                    }

                    // Sorted so that we maintain arg ordering and continue to get cache hits
                    List<String> patchChecks =
                            extension.getPatchChecks().get().stream().sorted().collect(Collectors.toList());

                    // If there are no checks to patch, we don't patch anything and just do a regular compile.
                    // The behaviour of "-XepPatchChecks:" is to patch *all* checks that are enabled, so we can't
                    // just leave it as that.
                    if (patchChecks.isEmpty()) {
                        return List.of();
                    }

                    return List.of("-XepPatchLocation:IN_PLACE", "-XepPatchChecks:" + String.join(",", patchChecks));
                }
            });
            return;
        }
    }

    private static boolean isRefactoring(Project project) {
        return project.hasProperty("errorProneApply");
    }

    private static boolean isStageOne(Project project) {
        return project.hasProperty(SuppressibleErrorPronePlugin.SUPPRESS_STAGE_ONE);
    }

    private static boolean isStageTwo(Project project) {
        return project.hasProperty(SuppressibleErrorPronePlugin.SUPPRESS_STAGE_TWO);
    }
}
