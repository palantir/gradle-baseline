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
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.ltgt.gradle.errorprone.CheckSeverity;
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
    private static final String SUPPRESS_STAGE_ONE = "errorProneSuppressStage1";
    private static final String SUPPRESS_STAGE_TWO = "errorProneSuppressStage2";
    private static final String ERROR_PRONE_APPLY = "errorProneApply";
    private static final Set<String> ERROR_PRONE_DISABLE = Set.of(
            "errorProneDisable",
            // This is only here for backcompat from when all the errorprone code lived in baseline
            "com.palantir.baseline-error-prone.disable");

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

        if (isSuppressingStageTwo(project)) {
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
                        configureErrorProneOptions(project, extension, javaCompile, errorProneOptions);
                    });
        });

        if (isAnyKindOfPatching(project)) {
            project.afterEvaluate(_ignored -> {
                // To allow refactoring near usages of deprecated methods, even when -Xlint:deprecation is specified,
                // we need to remove these compiler flags after all configuration has happened.
                project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
                    javaCompile.getOptions().setWarnings(false);
                    javaCompile.getOptions().setDeprecation(false);
                    javaCompile
                            .getOptions()
                            .setCompilerArgs(javaCompile.getOptions().getCompilerArgs().stream()
                                    .filter(arg -> !arg.equals("-Werror"))
                                    .filter(arg -> !arg.equals("-deprecation"))
                                    .filter(arg -> !arg.equals("-Xlint:deprecation"))
                                    .collect(Collectors.toList()));
                });
            });
        }
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

        // It's the annotationProcessor configuration, not the errorprone that, is actually used by the compiler
        // and so where we must put our transform. annotationProcessor extendsFrom errorprone.
        project.getConfigurations().named("annotationProcessor").configure(errorProneConfiguration -> {
            errorProneConfiguration
                    .getDependencies()
                    .add(project.getDependencies().create("com.google.errorprone:error_prone_check_api"));
            errorProneConfiguration.getAttributes().attribute(suppressiblified, true);
        });

        project.getDependencies().registerTransform(Suppressiblify.class, spec -> {
            // TODO: remove cachebust before merge
            spec.getParameters().getCacheBust().set(UUID.randomUUID().toString());
            spec.getParameters().getSuppressionStage1().set(isSuppressingStageOne(project));

            Attribute<String> artifactType = Attribute.of("artifactType", String.class);
            spec.getFrom().attribute(suppressiblified, false).attribute(artifactType, "jar");
            spec.getTo().attribute(suppressiblified, true).attribute(artifactType, "jar");
        });
    }

    private void configureJavaCompile(Project project, JavaCompile javaCompile) {
        if (isAnyKindOfPatching(project)) {
            // Don't attempt to cache since it won't capture the source files that might be modified
            javaCompile.getOutputs().cacheIf(t -> false);
        }
    }

    private void configureErrorProneOptions(
            Project project,
            SuppressibleErrorProneExtension extension,
            JavaCompile javaCompile,
            ErrorProneOptions errorProneOptions) {

        errorProneOptions.getEnabled().set(project.provider(() -> ERROR_PRONE_DISABLE.stream()
                .noneMatch(project::hasProperty)));

        // TODO: Fix this nebulatests
        // errorProneOptions.getExcludedPaths().set(excludedPathsRegex());

        if (isSuppressingStageOne(project)) {
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

        if (isSuppressingStageTwo(project)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return List.of("-XepPatchLocation:IN_PLACE", "-XepPatchChecks:SuppressWarningsCoalesce");
                }
            });
            return;
        }

        if (isApplyingSuggestedPatches(project)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    String possibleSpecificPatchChecks = (String) project.property(ERROR_PRONE_APPLY);
                    if (!(possibleSpecificPatchChecks == null || possibleSpecificPatchChecks.isBlank())) {
                        List<String> specificPatchChecks = Arrays.stream(possibleSpecificPatchChecks.split(","))
                                .map(String::trim)
                                .filter(Predicate.not(String::isEmpty))
                                .collect(Collectors.toList());

                        return List.of(
                                "-XepPatchLocation:IN_PLACE",
                                "-XepPatchChecks:" + String.join(",", specificPatchChecks));
                    }

                    List<String> patchChecks = extension.patchChecksForCompilation(javaCompile).stream()
                            // Do not patch checks that have been explicitly disabled
                            .filter(check ->
                                    errorProneOptions.getChecks().getting(check).getOrNull() != CheckSeverity.OFF)
                            // Sorted so that we maintain arg ordering and continue to get cache hits
                            .sorted()
                            .collect(Collectors.toList());

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

    private static boolean isAnyKindOfPatching(Project project) {
        return isApplyingSuggestedPatches(project) || isSuppressingStageOne(project) || isSuppressingStageTwo(project);
    }

    private static boolean isApplyingSuggestedPatches(Project project) {
        return project.hasProperty(ERROR_PRONE_APPLY);
    }

    private static boolean isSuppressingStageOne(Project project) {
        return project.hasProperty(SuppressibleErrorPronePlugin.SUPPRESS_STAGE_ONE);
    }

    private static boolean isSuppressingStageTwo(Project project) {
        return project.hasProperty(SuppressibleErrorPronePlugin.SUPPRESS_STAGE_TWO);
    }

    //    private static String excludedPathsRegex() {
    //        // Error-prone normalizes filenames to use '/' path separator:
    //        // https://github.com/google/error-prone/blob/c601758e81723a8efc4671726b8363be7a306dce
    //        // /check_api/src/main/java/com/google/errorprone/util/ASTHelpers.java#L1277-L1285
    //
    //        // language=RegExp
    //        return ".*/(build(?!nebulatest/)|generated_.*[sS]rc|src/generated.*)/.*";
    //    }
}
