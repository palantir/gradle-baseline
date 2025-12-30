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

import com.google.common.collect.Streams;
import com.palantir.baseline.plugins.BaselineExactDependencies;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class CheckImplicitDependenciesTask extends DefaultTask {

    private static final Comparator<ArtifactResult> ARTIFACT_COMPARATOR =
            Comparator.comparing(artifact -> artifact.getId().getDisplayName());

    @SuppressWarnings("for-rollout:GradleTypesAsFields")
    private final ListProperty<ResolvedComponentResult> dependenciesConfigurations;

    @SuppressWarnings("for-rollout:GradleTypesAsFields")
    private final Property<FileCollection> sourceClasses;

    @SuppressWarnings("for-rollout:GradleTypesAsFields")
    private final SetProperty<String> ignore;

    @SuppressWarnings("for-rollout:GradleTypesAsFields")
    private final Property<String> suggestionConfigurationName;

    public CheckImplicitDependenciesTask() {
        setGroup("Verification");
        setDescription("Ensures all dependencies are explicitly declared, not just transitively provided");
        dependenciesConfigurations = getProject().getObjects().listProperty(ResolvedComponentResult.class);
        dependenciesConfigurations.set(Collections.emptyList());
        sourceClasses = getProject().getObjects().property(FileCollection.class);
        ignore = getProject().getObjects().setProperty(String.class);
        ignore.set(Collections.emptySet());
        suggestionConfigurationName = getProject().getObjects().property(String.class);
    }

    @TaskAction
    public final void checkImplicitDependencies() {
        dependenciesConfigurations.get().stream()
                        .flatMap(result -> result.getDependencies().stream())
                                .map(result -> result.getRequested())
        dependenciesConfigurations.BaselineExactDependencies.INDEXES.populateIndexes(dependenciesConfigurations.get());

        Set<ResolvedArtifact> declaredArtifacts = resolvedConfigurations.stream()
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .filter(dependency ->
                        BaselineExactDependencies.VALID_ARTIFACT_EXTENSIONS.contains(dependency.getExtension()))
                .collect(Collectors.toSet());

        Set<List<ArtifactResult>> necessaryArtifacts = referencedClasses().stream()
                .map(c -> BaselineExactDependencies.INDEXES.classToArtifacts(c).toList())
                .collect(Collectors.toSet());

        List<ArtifactResult> usedButUndeclared = necessaryArtifacts.stream()
                .filter(artifacts -> artifacts.stream().noneMatch(this::isArtifactFromCurrentProject))
                .filter(artifacts -> artifacts.stream().noneMatch(this::shouldIgnore))
                .filter(artifacts -> artifacts.stream().noneMatch(declaredArtifacts::contains))
                // Select a single deterministic artifact for the suggestion
                .map(artifacts -> artifacts.stream().min(ARTIFACT_COMPARATOR))
                .<ResolvedArtifact>mapMulti(Optional::ifPresent)
                .sorted(ARTIFACT_COMPARATOR)
                .toList();
        if (!usedButUndeclared.isEmpty()) {
            String suggestion = usedButUndeclared.stream()
                    .map(this::getSuggestionString)
                    .mapMulti(Optional::ifPresent)
                    .sorted()
                    .collect(Collectors.joining("\n", "    dependencies {\n", "\n    }"));
            throw new ExceptionWithSuggestion(
                    String.format(
                            "Found %d implicit dependencies - consider adding the following explicit "
                                    + "dependencies to '%s', or avoid using classes from these jars:\n%s",
                            usedButUndeclared.size(), buildFile(), suggestion),
                    buildFile().toString());
        }
    }

    private Optional<String> getSuggestionString(ArtifactResult artifact) {
        ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
        if (componentId instanceof ProjectComponentIdentifier projectComponentId) {
            return Optional.of(String.format("project('%s')", projectComponentId.getProjectPath()));
        } else if (componentId instanceof ModuleComponentIdentifier moduleComponentId) {
            return Optional.of(String.format("'%s:%s'", moduleComponentId.getModuleIdentifier().getGroup(), moduleComponentId.getModuleIdentifier().getName()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an external jar.
     */
    private boolean isArtifactFromCurrentProject(ArtifactResult artifact) {
        ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();

        if (componentId instanceof ProjectComponentIdentifier projectId) {
            return projectId.getProjectPath().equals(getProject().getPath());
        } else {
            return false;
        }
    }

    /** All classes which are mentioned in this project's source code. */
    private Set<String> referencedClasses() {
        return Streams.stream(sourceClasses.get().iterator())
                .flatMap(BaselineExactDependencies::referencedClasses)
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("for-rollout:IllegalMethodCalledDuringTaskExecution")
    private Path buildFile() {
        return getProject()
                .getRootDir()
                .toPath()
                .relativize(getProject().getBuildFile().toPath());
    }

    private boolean shouldIgnore(ResolvedArtifact artifact) {
        return ignore.get().contains(BaselineExactDependencies.asString(artifact));
    }

    @Classpath
    public final ListProperty<ResolvedArtifactResult> getDependenciesConfigurations() {
        return dependenciesConfigurations;
    }

    public final void dependenciesConfiguration(Configuration dependenciesConfiguration) {
        this.dependenciesConfigurations.add(Objects.requireNonNull(dependenciesConfiguration
                .getDependencies()
                .getFirstLevelModuleDependencies()
                .getArtifacts()
                .get));
    }

    @Classpath
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
        ignore.add(BaselineExactDependencies.ignoreCoordinate(group, name));
    }

    @Input
    public final Provider<Set<String>> getIgnored() {
        return ignore;
    }

    @Input
    public final Provider<String> getSuggestionConfigurationName() {
        return suggestionConfigurationName;
    }

    public final void suggestionConfigurationName(String newSuggestionConfigurationName) {
        this.suggestionConfigurationName.set(Objects.requireNonNull(newSuggestionConfigurationName));
    }
}
