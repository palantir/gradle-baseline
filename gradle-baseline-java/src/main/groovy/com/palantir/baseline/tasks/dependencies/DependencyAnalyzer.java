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

package com.palantir.baseline.tasks.dependencies;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.paypal.digraph.parser.GraphParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.maven.shared.dependency.analyzer.ClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.Directory;

/**
 * Parses dot files listing dependencies and sorts them out.
 */
public final class DependencyAnalyzer {
    private static final ClassAnalyzer JAR_ANALYZER = new DefaultClassAnalyzer();
    private static final ImmutableSet<String> VALID_ARTIFACT_EXTENSIONS = ImmutableSet.of("jar", "");

    private final Project project;
    private final List<Configuration> dependenciesConfigurations;
    private final List<Configuration> sourceOnlyConfigurations;
    private final Directory dotFileDir;

    private boolean inited = false;
    private Set<ResolvedArtifact> allRequiredArtifacts;
    private Set<ResolvedArtifact> apiArtifacts;
    private Set<ResolvedArtifact> declaredArtifacts;
    private Set<ResolvedArtifact> sourceOnlyArtifacts;

    private final Indexes indexes = new Indexes();

    public DependencyAnalyzer(
            Project project,
            List<Configuration> dependenciesConfigurations,
            List<Configuration> sourceOnlyConfigurations,
            Directory dotFileDir) {
        this.project = project;
        this.dependenciesConfigurations = dependenciesConfigurations;
        this.sourceOnlyConfigurations = sourceOnlyConfigurations;
        this.dotFileDir = dotFileDir;
    }

    /**
     * Full list of artifacts that are required by the project.
     */
    public Set<ResolvedArtifact> getAllRequiredArtifacts() {
        init();
        return allRequiredArtifacts;
    }

    /**
     * Artifacts used in project's APIs - public or protected methods.
     */
    public Set<ResolvedArtifact> getApiArtifacts() {
        init();
        return apiArtifacts;
    }

    /**
     * Artifacts that are required but are not directly declared by the project.
     */
    public List<ResolvedArtifact> getImplicitDependencies() {
        init();
        return diffArtifacts(allRequiredArtifacts, declaredArtifacts);
    }

    /**
     * Artifacts that are declared, but not used.  If the sourceOnlyConfigurations are properly set, then excludes
     * dependencies that would be incorrectly flagged as unused  because they are not contained in byte code
     * (i.e. they are in annotations or other references that are source-only.
     */
    public List<ResolvedArtifact> getUnusedDependencies() {
        init();
        return diffArtifacts(declaredArtifacts, allRequiredArtifacts).stream()
                .filter(a -> !sourceOnlyArtifacts.contains(a))
                .collect(Collectors.toList());
    }

    private List<ResolvedArtifact> diffArtifacts(Set<ResolvedArtifact> base, Set<ResolvedArtifact> compare) {
        return Sets.difference(base, compare)
                .stream()
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .collect(Collectors.toList());
    }

    private void init() {
        if (inited) {
            return;
        }
        List<ResolvedDependency> declaredDependencies = resolvedDependencies(dependenciesConfigurations);
        indexes.populateIndexes(declaredDependencies);

        allRequiredArtifacts = findReferencedArtifacts(DependencyUtils.findDetailedDotReport(dotFileDir));
        apiArtifacts = findReferencedArtifacts(DependencyUtils.findDetailedDotReport(dotFileDir.dir("api")));
        declaredArtifacts = getResolvedArtifacts(declaredDependencies);
        sourceOnlyArtifacts = resolveConfigurationArtifacts(sourceOnlyConfigurations);
        inited = true;
        //clear the memory from the massive dependency map
        indexes.reset();
    }

    private static Set<ResolvedArtifact> resolveConfigurationArtifacts(Collection<Configuration> configurations) {
        Collection<ResolvedDependency> resolvedDeps = resolvedDependencies(configurations);
        return getResolvedArtifacts(resolvedDeps);
    }

    /**
     * Returns all dependencies for given configurations.  Cannot use a Set because dependency object ids for
     * hashing do not account for classifiers.
     */
    private static List<ResolvedDependency> resolvedDependencies(Collection<Configuration> configurations) {
        return configurations.stream()
                .map(Configuration::getResolvedConfiguration)
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .collect(Collectors.toList());
    }

    private static Set<ResolvedArtifact> getResolvedArtifacts(Collection<ResolvedDependency> dependencies) {
        return getResolvedArtifacts(dependencies, false);
    }

    /**
     * All artifacts with valid extensions (i.e. jar) from the dependencies, optionally including
     * transitives.  Artifact identifiers are unique for hashing, so can return a set.
     */
    private static Set<ResolvedArtifact> getResolvedArtifacts(Collection<ResolvedDependency> dependencies,
                                                              boolean recursive) {
        return dependencies.stream()
                .flatMap(dependency -> (recursive ? dependency.getAllModuleArtifacts()
                        : dependency.getModuleArtifacts()).stream())
                .filter(dependency -> VALID_ARTIFACT_EXTENSIONS.contains(dependency.getExtension()))
                .collect(Collectors.toSet());
    }

    private Set<ResolvedArtifact> findReferencedArtifacts(Optional<File> dotFile) {
        return Streams.stream(dotFile)
                .flatMap(this::findReferencedClasses)
                .map(indexes::classToDependency)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(x -> !isArtifactFromCurrentProject(x))
                .collect(Collectors.toSet());
    }

    /**
     * Return all classes that are referenced (i.e. depended upon) by classes in the given dot file.
     */
    private Stream<String> findReferencedClasses(File dotFile) {
        try (InputStream input = new FileInputStream(dotFile)) {
            GraphParser parser = new GraphParser(input);
            return parser.getEdges()
                    .values()
                    .stream()
                    .map(e -> e.getNode2().getId())
                    .map(DependencyAnalyzer::cleanDependencyName);
        } catch (IOException e) {
            throw new RuntimeException("Unable to analyze " + dotFile, e);
        }
    }

    /**
     * Strips excess junk written by jdeps.
     */
    private static String cleanDependencyName(String name) {
        return name.replaceAll(" \\([^)]*\\)", "").replace("\"", "");
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an
     * external jar.
     */
    private boolean isArtifactFromCurrentProject(ResolvedArtifact artifact) {
        if (!DependencyUtils.isProjectDependency(artifact)) {
            return false;
        }
        return ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier()).getProjectPath()
                .equals(project.getPath());
    }

    @ThreadSafe
    public static final class Indexes {
        private final Map<String, ResolvedArtifact> classToDependency = new ConcurrentHashMap<>();

        public void populateIndexes(Collection<ResolvedDependency> declaredDependencies) {
            Collection<ResolvedArtifact> allArtifacts = getResolvedArtifacts(declaredDependencies, true);

            allArtifacts.forEach(artifact -> {
                try {
                    File jar = artifact.getFile();

                    Set<String> classesInArtifact = JAR_ANALYZER.analyze(jar.toURI().toURL());
                    classesInArtifact.forEach(clazz -> classToDependency.put(clazz, artifact));
                } catch (IOException e) {
                    throw new RuntimeException("Unable to analyze artifact", e);
                }
            });
        }

        /** Given a class, what dependency brought it in. */
        public Optional<ResolvedArtifact> classToDependency(String clazz) {
            return Optional.ofNullable(classToDependency.get(clazz));
        }

        public void reset() {
            classToDependency.clear();
        }
    }

}
