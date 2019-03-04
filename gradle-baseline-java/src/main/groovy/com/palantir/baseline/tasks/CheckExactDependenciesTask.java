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

package com.palantir.baseline.tasks;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.palantir.baseline.plugins.BaselineExactDependencies;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

public class CheckExactDependenciesTask extends DefaultTask {

    private final ListProperty<Configuration> dependenciesConfigurations;
    private final Property<FileCollection> classes;
    private final SetProperty<String> allowExtraneous;

    public CheckExactDependenciesTask() {
        setGroup("Verification");
        dependenciesConfigurations = getProject().getObjects().listProperty(Configuration.class);
        dependenciesConfigurations.set(Collections.emptyList());
        classes = getProject().getObjects().property(FileCollection.class);
        allowExtraneous = getProject().getObjects().setProperty(String.class);
    }

    @TaskAction
    public final void checkUnusedDependencies() {

        Set<ResolvedDependency> declaredDependencies = dependenciesConfigurations.get().stream()
                .map(Configuration::getResolvedConfiguration)
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .collect(Collectors.toSet());

        BaselineExactDependencies.INDEXES.populateIndexes(declaredDependencies);

        // Gather the set of expected dependencies based on referenced classes
        Set<ResolvedArtifact> expectedArtifacts = referencedClasses().stream()
                .map(BaselineExactDependencies.INDEXES::classToDependency)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        Set<ResolvedArtifact> declaredArtifacts = declaredDependencies.stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .collect(Collectors.toSet());

        List<ResolvedArtifact> usedButUndeclared = Sets.difference(expectedArtifacts, declaredArtifacts).stream()
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .collect(Collectors.toList());

        // TODO(dfox): don't print warnings for jars that define service loaded classes (e.g. meta-inf)
        List<ResolvedArtifact> declaredButUnused = Sets.difference(declaredArtifacts, expectedArtifacts).stream()
                .filter(artifact -> !allowedExtraneous(artifact))
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .collect(Collectors.toList());

        if (!usedButUndeclared.isEmpty()) {
            // TODO(dfox): suggest project(':project-name') when a jar actually comes from this project!
            String suggestion = usedButUndeclared.stream()
                    .filter(artifact -> !allowedExtraneous(artifact))
                    .map(artifact -> String.format("        implementation '%s:%s'",
                            artifact.getModuleVersion().getId().getGroup(),
                            artifact.getModuleVersion().getId().getName()))
                    .sorted()
                    .collect(Collectors.joining("\n", "    dependencies {\n", "\n    }"));

            getLogger().warn("Found {} implicit dependencies - consider adding the following explicit "
                            + "dependencies to '{}', or avoid using classes from these jars:\n{}",
                    usedButUndeclared.size(),
                    buildFile(),
                    suggestion);
        }

        if (!declaredButUnused.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %s extraneous dependencies, please delete them from '%s' or "
                    + "choose one of the suggested fixes:\n", declaredButUnused.size(), buildFile()));
            for (ResolvedArtifact resolvedArtifact : declaredButUnused) {
                sb.append('\t').append(asString(resolvedArtifact)).append('\n');

                // Suggest fixes by looking at all transitive classes, filtering the ones we have declarations on,
                // and mapping the remaining ones back to the jars they came from.
                ResolvedDependency dependency = BaselineExactDependencies.INDEXES
                        .artifactsFromDependency(resolvedArtifact);
                Set<ResolvedArtifact> didYouMean = dependency.getAllModuleArtifacts().stream()
                        .flatMap(BaselineExactDependencies.INDEXES::classesFromArtifact)
                        .filter(referencedClasses()::contains)
                        .map(BaselineExactDependencies.INDEXES::classToDependency)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .filter(artifact -> !declaredArtifacts.contains(artifact))
                        .collect(Collectors.toSet());

                if (!didYouMean.isEmpty()) {
                    sb.append("\t\tDid you mean:\n");
                    didYouMean.forEach(transitive -> sb.append("\t\t\timplementation '")
                            .append(asString(transitive))
                            .append("\' \n"));
                }
            }
            throw new GradleException(sb.toString());
        }
    }

    private Path buildFile() {
        return getProject().getRootDir().toPath().relativize(getProject().getBuildFile().toPath());
    }

    private boolean allowedExtraneous(ResolvedArtifact artifact) {
        return allowExtraneous.get().contains(asString(artifact));
    }

    private static String asString(ResolvedArtifact artifact) {
        ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
        return id.getGroup() + ":" + id.getName();
    }

    /** All classes which are mentioned in this project's source code. */
    private Set<String> referencedClasses() {
        return Streams.stream(classes.get().iterator()).flatMap(classFile -> {
            try {
                return BaselineExactDependencies.CLASS_FILE_ANALYZER.analyze(classFile.toURI().toURL()).stream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }

    @Input
    public final Provider<List<Configuration>> getDependenciesConfigurations() {
        return dependenciesConfigurations;
    }

    public final void dependenciesConfiguration(Configuration dependenciesConfiguration) {
        this.dependenciesConfigurations.add(Objects.requireNonNull(dependenciesConfiguration));
    }

    @InputFiles
    public final Provider<FileCollection> getClasses() {
        return classes;
    }

    public final void setClasses(FileCollection newClasses) {
        this.classes.set(getProject().files(newClasses));
    }

    public final void allowExtraneous(Provider<Set<String>> value) {
        allowExtraneous.set(value);
    }

    @Input
    public final Provider<Set<String>> getAllowExtraneous() {
        return allowExtraneous;
    }
}
