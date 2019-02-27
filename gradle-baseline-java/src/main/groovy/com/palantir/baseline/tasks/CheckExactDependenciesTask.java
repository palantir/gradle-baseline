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

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.shared.dependency.analyzer.ClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

@SuppressWarnings("DesignForExtension")
public class CheckExactDependenciesTask extends DefaultTask {

    private static final ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
    private static final DependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();

    private List<Configuration> dependenciesConfigurations = new ArrayList<>();
    private FileCollection classes;

    // indexes, populated using the above analyzers
    private final Map<String, ResolvedArtifact> classToDependency = new HashMap<>();
    private final Map<ResolvedArtifact, Set<String>> classesFromArtifact = new HashMap<>();
    private final Map<ResolvedArtifact, ResolvedDependency> artifactsFromDependency = new HashMap<>();

    public CheckExactDependenciesTask() {
        setGroup("Verification");
    }

    @TaskAction
    public void checkUnusedDependencies() {

        Set<ResolvedDependency> declaredDependencies = getDependenciesConfigurations().stream()
                .map(Configuration::getResolvedConfiguration)
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .collect(Collectors.toSet());

        Set<ResolvedArtifact> declaredArtifacts = declaredDependencies.stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .collect(Collectors.toSet());

        Set<ResolvedArtifact> allArtifacts = declaredDependencies.stream()
                .flatMap(dependency -> dependency.getAllModuleArtifacts().stream())
                .collect(Collectors.toSet());

        populateIndexes(declaredDependencies, allArtifacts);

        // Gather the set of expected dependencies based on referenced classes
        Set<ResolvedArtifact> expectedArtifacts = referencedClasses().stream()
                .map(clazz -> {
                    ResolvedArtifact maybeArtifact = classToDependency.get(clazz);
                    if (maybeArtifact == null) {
                        getLogger().debug("Failed to locate artifact for {}", clazz);
                    }
                    return maybeArtifact;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ResolvedArtifact> usedButUndeclared = Sets.difference(expectedArtifacts, declaredArtifacts).stream()
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .collect(Collectors.toList());

        List<ResolvedArtifact> declaredButUnused = Sets.difference(declaredArtifacts, expectedArtifacts).stream()
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .collect(Collectors.toList());

        if (!usedButUndeclared.isEmpty()) {
            getLogger().warn(
                    "Used but undeclared dependencies:\n{}",
                    Joiner.on("\t\n").join(usedButUndeclared));
        }

        if (!declaredButUnused.isEmpty()) {
            StringBuilder sb = new StringBuilder("Declared but unused dependencies:\n");
            for (ResolvedArtifact resolvedArtifact : declaredButUnused) {
                sb.append('\t').append(resolvedArtifact).append('\n');

                // Find the original first-level dependency corresponding to this artifact
                ResolvedDependency dependency = artifactsFromDependency.get(resolvedArtifact);

                // Suggest fixes by looking at all transitive classes, filtering the ones we have declarations on,
                // and mapping the remaining ones back to the jars they came from.
                Set<ResolvedArtifact> didYouMean = dependency.getAllModuleArtifacts().stream()
                        .flatMap(artifact -> classesFromArtifact.get(artifact).stream())
                        .filter(referencedClasses()::contains)
                        .map(clazz -> classToDependency.get(clazz))
                        .filter(Objects::nonNull)
                        .filter(artifact -> !declaredArtifacts.contains(artifact))
                        .collect(Collectors.toSet());

                if (!didYouMean.isEmpty()) {
                    sb.append("\t\tDid you mean:\n");
                    didYouMean.forEach(transitive -> sb.append("\t\t\t").append(transitive).append('\n'));
                }
            }
            throw new GradleException(sb.toString());
        }
    }

    private void populateIndexes(Set<ResolvedDependency> declaredDependencies, Set<ResolvedArtifact> allArtifacts) {
        allArtifacts.forEach(artifact -> {
            try {
                // Construct class/artifact maps
                Set<String> classesInArtifact = classAnalyzer.analyze(artifact.getFile().toURI().toURL());
                classesFromArtifact.put(artifact, classesInArtifact);
                classesInArtifact.forEach(clazz -> {
                    if (classToDependency.put(clazz, artifact) != null) {
                        getLogger().info("Found duplicate class {}", clazz);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Unable to analyze artifact", e);
            }
        });

        declaredDependencies.stream().forEach(dependency -> {
            Set<ResolvedArtifact> artifacts = dependency.getModuleArtifacts();
            artifacts.forEach(artifact -> artifactsFromDependency.put(artifact, dependency));
        });
    }

    /** All classes which are mentioned in this project's source code. */
    private Set<String> referencedClasses() {
        return Streams.stream(classes.iterator())
                .flatMap(file -> {
                    try {
                        return dependencyAnalyzer.analyze(file.toURI().toURL()).stream();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());
    }

    @Input
    public List<Configuration> getDependenciesConfigurations() {
        return dependenciesConfigurations.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void setDependenciesConfigurations(List<Configuration> dependenciesConfigurations) {
        this.dependenciesConfigurations = new ArrayList<>(dependenciesConfigurations);
    }

    public void dependenciesConfiguration(Configuration dependenciesConfiguration) {
        this.dependenciesConfigurations.add(dependenciesConfiguration);
    }

    @InputFiles
    public FileCollection getClasses() {
        return classes;
    }

    public void setClasses(Object newClasses) {
        this.classes = getProject().files(newClasses);
    }

}
