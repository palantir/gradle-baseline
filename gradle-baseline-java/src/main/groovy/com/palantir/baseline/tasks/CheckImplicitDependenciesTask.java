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

public class CheckImplicitDependenciesTask extends DefaultTask {

    private final ListProperty<Configuration> dependenciesConfigurations;
    private final Property<FileCollection> classes;
    private final SetProperty<String> allowExtraneous;

    public CheckImplicitDependenciesTask() {
        setGroup("Verification");
        dependenciesConfigurations = getProject().getObjects().listProperty(Configuration.class);
        dependenciesConfigurations.set(Collections.emptyList());
        classes = getProject().getObjects().property(FileCollection.class);
        allowExtraneous = getProject().getObjects().setProperty(String.class);
    }

    @TaskAction
    public final void checkImplicitDependencies() {
        Set<ResolvedDependency> declaredDependencies = dependenciesConfigurations.get().stream()
                .map(Configuration::getResolvedConfiguration)
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .collect(Collectors.toSet());
        BaselineExactDependencies.INDEXES.populateIndexes(declaredDependencies);

        Set<ResolvedArtifact> necessaryArtifacts = referencedClasses().stream()
                .map(BaselineExactDependencies.INDEXES::classToDependency)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        Set<ResolvedArtifact> declaredArtifacts = declaredDependencies.stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .collect(Collectors.toSet());

        List<ResolvedArtifact> usedButUndeclared = Sets.difference(necessaryArtifacts, declaredArtifacts).stream()
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

            throw new GradleException(
                    String.format("Found %d implicit dependencies - consider adding the following explicit "
                            + "dependencies to '%s', or avoid using classes from these jars:\n%s",
                    usedButUndeclared.size(),
                    buildFile(),
                    suggestion));
        }
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
