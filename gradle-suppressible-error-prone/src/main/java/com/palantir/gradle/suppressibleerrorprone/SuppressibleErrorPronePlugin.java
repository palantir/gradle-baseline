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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
                                "com.palantir.baseline:suppressible-errorprone-annotations:" + version);
            });
        }

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            configureJavaCompile(project, javaCompile);

            ((ExtensionAware) javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        configureErrorProneOptions(project, errorProneOptions);
                    });
        });
    }

    private void configureJavaCompile(Project project, JavaCompile javaCompile) {
        // TODO(callumr): OK to be duplicated between here and BaselineErrorProne?
        if (isStageOne(project) || isStageTwo(project)) {
            // Don't attempt to cache since it won't capture the source files that might be modified
            javaCompile.getOutputs().cacheIf(t -> false);
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

        project.getConfigurations().named("annotationProcessor").configure(errorProneConfiguration -> {
            errorProneConfiguration.getAttributes().attribute(suppressiblified, true);
        });

        project.getDependencies().registerTransform(Suppressiblify.class, spec -> {
            spec.getParameters().getCacheBust().set(UUID.randomUUID().toString());
            Attribute<String> artifactType = Attribute.of("artifactType", String.class);
            spec.getFrom().attribute(suppressiblified, false).attribute(artifactType, "jar");
            spec.getTo().attribute(suppressiblified, true).attribute(artifactType, "jar");
        });
    }

    private void configureErrorProneOptions(Project project, ErrorProneOptions errorProneOptions) {
        if (isStageOne(project)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return List.of("-XepOpt:" + SuppressibleErrorPronePlugin.SUPPRESS_STAGE_ONE + "=true");
                }
            });
        }

        if (isStageTwo(project)) {
            errorProneOptions.getErrorproneArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return List.of("-XepPatchLocation:IN_PLACE", "-XepPatchChecks:SuppressWarningsCoalesce");
                }
            });
        }
    }

    private static boolean isStageOne(Project project) {
        return project.hasProperty(SuppressibleErrorPronePlugin.SUPPRESS_STAGE_ONE);
    }

    private static boolean isStageTwo(Project project) {
        return project.hasProperty(SuppressibleErrorPronePlugin.SUPPRESS_STAGE_TWO);
    }
}