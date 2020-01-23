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
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

public class CheckUnusedDependenciesTask extends DefaultTask {

    private final ListProperty<Configuration> dependenciesConfigurations;
    private final ListProperty<Configuration> sourceOnlyConfigurations;
    private final Property<FileCollection> sourceClasses;
    private final SetProperty<String> ignore;

    public CheckUnusedDependenciesTask() {
        setGroup("Verification");
        setDescription("Ensures no extraneous dependencies are declared");
        dependenciesConfigurations = getProject().getObjects().listProperty(Configuration.class);
        dependenciesConfigurations.set(Collections.emptyList());
        sourceOnlyConfigurations = getProject().getObjects().listProperty(Configuration.class);
        sourceOnlyConfigurations.set(Collections.emptyList());
        sourceClasses = getProject().getObjects().property(FileCollection.class);
        ignore = getProject().getObjects().setProperty(String.class);
        ignore.set(Collections.emptySet());
    }

    @TaskAction
    public final void checkUnusedDependencies() {
        Set<ResolvedDependency> declaredDependencies = dependenciesConfigurations.get().stream()
                .map(Configuration::getResolvedConfiguration)
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .collect(Collectors.toSet());
        BaselineExactDependencies.INDEXES.populateIndexes(declaredDependencies);

        Set<ResolvedArtifact> necessaryArtifacts = Streams.stream(
                        sourceClasses.get().iterator())
                .flatMap(BaselineExactDependencies::referencedClasses)
                .map(BaselineExactDependencies.INDEXES::classToDependency)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        Set<ResolvedArtifact> declaredArtifacts = declaredDependencies.stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .filter(dependency ->
                        BaselineExactDependencies.VALID_ARTIFACT_EXTENSIONS.contains(dependency.getExtension()))
                .collect(Collectors.toSet());

        excludeSourceOnlyDependencies();

        Set<ResolvedArtifact> possiblyUnused = Sets.difference(declaredArtifacts, necessaryArtifacts);
        getLogger()
                .debug(
                        "Possibly unused dependencies: {}",
                        possiblyUnused.stream()
                                .map(BaselineExactDependencies::asString)
                                .sorted()
                                .collect(Collectors.toList()));
        List<ResolvedArtifact> declaredButUnused = possiblyUnused.stream()
                .filter(artifact -> !shouldIgnore(artifact))
                .sorted(Comparator.comparing(BaselineExactDependencies::asString))
                .collect(Collectors.toList());
        if (!declaredButUnused.isEmpty()) {
            // TODO(dfox): don't print warnings for jars that define service loaded classes (e.g. meta-inf)
            StringBuilder builder = new StringBuilder();
            builder.append(String.format(
                    "Found %s dependencies unused during compilation, please delete them from '%s' or choose one of "
                            + "the suggested fixes:\n",
                    declaredButUnused.size(), buildFile()));
            for (ResolvedArtifact resolvedArtifact : declaredButUnused) {
                builder.append('\t')
                        .append(BaselineExactDependencies.asDependencyStringWithName(resolvedArtifact))
                        .append('\n');

                // Suggest fixes by looking at all transitive classes, filtering the ones we have declarations on,
                // and mapping the remaining ones back to the jars they came from.
                ResolvedDependency dependency =
                        BaselineExactDependencies.INDEXES.artifactsFromDependency(resolvedArtifact);
                Set<ResolvedArtifact> didYouMean = dependency.getAllModuleArtifacts().stream()
                        .filter(artifact ->
                                BaselineExactDependencies.VALID_ARTIFACT_EXTENSIONS.contains(artifact.getExtension()))
                        .flatMap(BaselineExactDependencies.INDEXES::classesFromArtifact)
                        .filter(referencedClasses()::contains)
                        .map(BaselineExactDependencies.INDEXES::classToDependency)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .filter(artifact -> !declaredArtifacts.contains(artifact))
                        .collect(Collectors.toSet());

                if (!didYouMean.isEmpty()) {
                    builder.append("\t\tDid you mean:\n");
                    didYouMean.stream()
                            .map(BaselineExactDependencies::asDependencyStringWithoutName)
                            .sorted()
                            .forEach(dependencyString -> builder.append("\t\t\timplementation ")
                                    .append(dependencyString)
                                    .append("\n"));
                }
            }
            throw new GradleException(builder.toString());
        }
    }

    /**
     * Excludes compileOnly and annotationProcessor dependencies as they would be incorrectly flagged as unused by this
     * task due to BaselineExactDependencies use of
     * {@link org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer} which only looks at the
     * dependencies of the generated byte-code, not the union of compile + runtime dependencies.
     */
    private void excludeSourceOnlyDependencies() {
        sourceOnlyConfigurations.get().forEach(config ->
                config.getResolvedConfiguration().getFirstLevelModuleDependencies().stream()
                        .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                        .forEach(artifact -> ignoreDependency(config, artifact)));
    }

    private void ignoreDependency(Configuration config, ResolvedArtifact artifact) {
        String dependencyId = BaselineExactDependencies.asString(artifact);
        getLogger().info("Ignoring {} dependency: {}", config.getName(), dependencyId);
        ignore.add(dependencyId);
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

    @Input
    public final Provider<List<Configuration>> getSourceOnlyConfigurations() {
        return sourceOnlyConfigurations;
    }

    public final void sourceOnlyConfiguration(Configuration configuration) {
        this.sourceOnlyConfigurations.add(Objects.requireNonNull(configuration));
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
