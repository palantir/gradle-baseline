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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.FileCollection;

public class ImplicitDependenciesAnalyzer {
    private final Project project;
    private final List<Configuration> dependenciesConfigurations;
    private final FileCollection sourceClasses;
    private final Set<String> ignore;

    public ImplicitDependenciesAnalyzer(
            Project project, List<Configuration> dependenciesConfigurations,
            FileCollection sourceClasses, Set<String> ignore) {
        this.project = project;
        this.dependenciesConfigurations = dependenciesConfigurations;
        this.sourceClasses = sourceClasses;
        this.ignore = ignore;
    }

    public final List<ResolvedArtifact> findImplicitDependencies() {
        Set<ResolvedDependency> declaredDependencies = dependenciesConfigurations.stream()
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

        return Sets.difference(necessaryArtifacts, declaredArtifacts).stream()
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .filter(artifact -> !shouldIgnore(artifact))
                .collect(Collectors.toList());
    }


    /** All classes which are mentioned in this project's source code. */
    private Set<String> referencedClasses() {
        return Streams.stream(sourceClasses.iterator())
                .flatMap(BaselineExactDependencies::referencedClasses)
                .collect(Collectors.toSet());
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an
     * external jar.
     */
    private boolean isProjectArtifact(ResolvedArtifact artifact) {
        return artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier;
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an
     * external jar.
     */
    private boolean isArtifactFromCurrentProject(ResolvedArtifact artifact) {
        if (!isProjectArtifact(artifact)) {
            return false;
        }
        return ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier()).getProjectPath()
                .equals(project.getPath());
    }

    private boolean shouldIgnore(ResolvedArtifact artifact) {
        return ignore.contains(getArtifactNameRep(artifact));
    }

    public final String getArtifactNameRep(ResolvedArtifact artifact) {
        return isProjectArtifact(artifact)
                ? String.format("project('%s')",
                ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier()).getProjectPath())
                : String.format("'%s:%s'",
                        artifact.getModuleVersion().getId().getGroup(), artifact.getModuleVersion().getId().getName());
    }

}
