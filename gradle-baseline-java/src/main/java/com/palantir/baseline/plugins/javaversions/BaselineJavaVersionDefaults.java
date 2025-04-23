/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins.javaversions;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

public final class BaselineJavaVersionDefaults implements Plugin<Project> {
    private static final int LIBRARY_TARGET = 17;
    private static final int DISTRIBUTION_TARGET = 21;

    @SuppressWarnings("UnusedMethod")
    private static IsLibraryWithReason isLibraryInIsolation(Project project) {
        PublishingExtension publishingExtension = project.getExtensions().findByType(PublishingExtension.class);

        if (publishingExtension == null) {
            return new IsLibraryWithReason(false, "doesn't have any publications");
        }

        NamedDomainObjectSet<MavenPublication> mavenPublications =
                publishingExtension.getPublications().withType(MavenPublication.class);

        if (mavenPublications.isEmpty()) {
            return new IsLibraryWithReason(false, "doesn't have any maven publications");
        }

        StringBuilder jarArtifactsReason = new StringBuilder();

        for (MavenPublication mavenPublication : mavenPublications) {
            List<String> jarArtifactRelativePaths = mavenPublication.getArtifacts().stream()
                    .filter(mavenArtifact -> mavenArtifact.getExtension().equals("jar"))
                    .map(mavenArtifact -> project.getRootDir()
                            .toPath()
                            .relativize(mavenArtifact.getFile().toPath())
                            .toString())
                    .toList();

            if (jarArtifactRelativePaths.isEmpty()) {
                continue;
            }

            jarArtifactsReason.append("publication '%s' publishes jar artifacts:\n\t%s"
                    .formatted(mavenPublication.getName(), String.join("\n\t", jarArtifactRelativePaths)));
        }

        if (jarArtifactsReason.isEmpty()) {
            return new IsLibraryWithReason(false, "doesn't have any jar artifacts in any of it's maven publications");
        }

        return new IsLibraryWithReason(true, jarArtifactsReason.toString());
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BaselineJavaVersion.class);

        BaselineJavaVersionExtension extension = project.getExtensions().getByType(BaselineJavaVersionExtension.class);

        TaskProvider<TargetJavaVersion> maxJavaVersion = project.getTasks()
                .register("targetJavaVersion", TargetJavaVersion.class, task -> {
                    task.getOutputFile().set(new File(task.getTemporaryDir(), "target-java-version"));
                    task.getTarget().set(extension.target());
                });

        NamedDomainObjectProvider<Configuration> javaDependencies = project.getConfigurations()
                .register("javaDependencies", conf -> {
                    conf.setCanBeResolved(false);

                    project.getRootProject().getAllprojects().forEach(otherProject -> {
                        if (otherProject.equals(project)) {
                            return;
                        }

                        Attribute<Boolean> attribute = javaDependenyOnAttribute(otherProject);
                        conf.getAttributes()
                                .attributeProvider(
                                        attribute,
                                        project.getConfigurations()
                                                .named("compileClasspath")
                                                .map(item -> {
                                                    System.out.println("CALCULATED: " + attribute.getName() + " on "
                                                            + project.getPath());
                                                    return item;
                                                })
                                                .map(compileClasspath -> projectIsDependedOnByConfiguration(
                                                        otherProject, compileClasspath)));
                    });
                });

        project.getArtifacts().add(javaDependencies.getName(), maxJavaVersion);

        Provider<Configuration> javaDependents = project.getConfigurations().register("javaDependents", conf -> {
            project.getRootProject().getAllprojects().forEach(otherProject -> {
                if (otherProject.equals(project)) {
                    return;
                }
                conf.getDependencies().add(project.getDependencies().create(otherProject));
                conf.setCanBeConsumed(false);
                conf.getAttributes().attribute(javaDependenyOnAttribute(project), true);
            });
        });

        project.getTasks().register("getDependents", GetDependents.class, task -> {
            task.getDependentProjects().set(javaDependents.map(conf -> conf
                    .getIncoming()
                    .artifactView(viewConfiguration -> {
                        viewConfiguration.setLenient(true);
                    })
                    .getArtifacts()
                    .getArtifacts()
                    .stream()
                    .filter(artifact -> Optional.ofNullable(artifact.getVariant()
                                    .getAttributes()
                                    .getAttribute(javaDependenyOnAttribute(project)))
                            .equals(Optional.of(true)))
                    .map(artifact -> artifact.getId().getComponentIdentifier().getDisplayName())
                    .collect(Collectors.toSet())));
        });
    }

    private static @NotNull Attribute<Boolean> javaDependenyOnAttribute(Project project) {
        return Attribute.of("javaDependencyOn" + project.getPath(), Boolean.class);
    }

    public abstract static class GetDependents extends DefaultTask {
        @Input
        public abstract SetProperty<String> getDependentProjects();

        @TaskAction
        public final void action() {
            getLogger().lifecycle("Dependents: {}", getDependentProjects().get());
        }
    }

    private static boolean projectIsDependedOnByConfiguration(Project otherProject, Configuration compileClasspath) {
        return compileClasspath.getIncoming().getResolutionResult().getAllComponents().stream()
                .anyMatch(resolvedComponentResult -> {
                    if (!(resolvedComponentResult.getId() instanceof ProjectComponentIdentifier projectId)) {
                        return false;
                    }

                    return projectId.getProjectPath().equals(otherProject.getPath());
                });
    }
}
