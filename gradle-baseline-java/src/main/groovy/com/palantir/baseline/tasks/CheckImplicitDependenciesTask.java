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
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
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
    private final Property<FileCollection> sourceClasses;
    private final SetProperty<String> ignore;

    public CheckImplicitDependenciesTask() {
        setGroup("Verification");
        setDescription("Ensures all dependencies are explicitly declared, not just transitively provided");
        dependenciesConfigurations = getProject().getObjects().listProperty(Configuration.class);
        dependenciesConfigurations.set(Collections.emptyList());
        sourceClasses = getProject().getObjects().property(FileCollection.class);
        ignore = getProject().getObjects().setProperty(String.class);
        ignore.set(Collections.emptySet());
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
                .filter(x -> !isArtifactFromCurrentProject(x))
                .collect(Collectors.toSet());
        Set<ResolvedArtifact> declaredArtifacts = declaredDependencies.stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .collect(Collectors.toSet());

        List<ResolvedArtifact> usedButUndeclared = Sets.difference(necessaryArtifacts, declaredArtifacts).stream()
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .filter(artifact -> !shouldIgnore(artifact))
                .collect(Collectors.toList());
        if (!usedButUndeclared.isEmpty()) {
            String suggestion = usedButUndeclared.stream()
                    .map(artifact -> getSuggestionString(artifact))
                    .sorted()
                    .collect(Collectors.joining("\n", "    dependencies {\n", "\n    }"));

            throw new GradleException(String.format(
                    "Found %d implicit dependencies - consider adding the following explicit "
                            + "dependencies to '%s', or avoid using classes from these jars:\n%s",
                    usedButUndeclared.size(), buildFile(), suggestion));
        }
    }

    private String getSuggestionString(ResolvedArtifact artifact) {
        String artifactNameString = isProjectArtifact(artifact)
                ? String.format(
                        "project('%s')",
                        ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier()).getProjectPath())
                : String.format(
                        "'%s:%s'",
                        artifact.getModuleVersion().getId().getGroup(),
                        artifact.getModuleVersion().getId().getName());
        return String.format("        implementation %s", artifactNameString);
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an external jar.
     */
    private boolean isProjectArtifact(ResolvedArtifact artifact) {
        return artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier;
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an external jar.
     */
    private boolean isArtifactFromCurrentProject(ResolvedArtifact artifact) {
        if (!isProjectArtifact(artifact)) {
            return false;
        }
        return ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier())
                .getProjectPath()
                .equals(getProject().getPath());
    }

    /** All classes which are mentioned in this project's source code. */
    private Set<String> referencedClasses() {
        return Streams.stream(sourceClasses.get().iterator())
                .flatMap(BaselineExactDependencies::referencedClasses)
                .collect(Collectors.toSet());
    }

    private Path buildFile() {
        return getProject()
                .getRootDir()
                .toPath()
                .relativize(getProject().getBuildFile().toPath());
    }

    private boolean shouldIgnore(ResolvedArtifact artifact) {
        return ignore.get().contains(BaselineExactDependencies.asString(artifact));
    }

    @Input
    public final Provider<List<Configuration>> getDependenciesConfigurations() {
        return dependenciesConfigurations;
    }

    public final void dependenciesConfiguration(Configuration dependenciesConfiguration) {
        this.dependenciesConfigurations.add(Objects.requireNonNull(dependenciesConfiguration));
    }

    @InputFiles
    public final Provider<FileCollection> getSourceClasses() {
        return sourceClasses;
    }

    public final void setSourceClasses(FileCollection newClasses) {
        this.sourceClasses.set(getProject().files(newClasses));
    }

    public final void ignore(Provider<Set<String>> value) {
        ignore.set(value);
    }

    public final void ignore(String group, String name) {
        ignore.add(group + ":" + name);
    }

    @Input
    public final Provider<Set<String>> getIgnored() {
        return ignore;
    }
}
