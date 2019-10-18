/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import org.gradle.api.file.FileCollection;

/**
 * Parses dot files listing dependencies and sorts them out
 */
public final class DependencyAnalyzer {
    private static final ClassAnalyzer JAR_ANALYZER = new DefaultClassAnalyzer();
    private static final ImmutableSet<String> VALID_ARTIFACT_EXTENSIONS = ImmutableSet.of("jar", "");

    private final Project project;
    private final List<Configuration> dependenciesConfigurations;
    private final FileCollection fullDepFiles;
    private final FileCollection apiDepFiles;
    private final Set<String> ignore;

    private boolean inited = false;
    private Set<ResolvedArtifact> allRequiredArtifacts;
    private Set<ResolvedArtifact> apiArtifacts;
    private Set<ResolvedArtifact> declaredArtifacts;

    private final Indexes indexes = new Indexes();

    public DependencyAnalyzer(
            Project project,
            List<Configuration> dependenciesConfigurations,
            FileCollection fullDepFiles,
            FileCollection apiDepFiles,
            Set<String> ignore) {
        this.project = project;
        this.dependenciesConfigurations = dependenciesConfigurations;
        this.fullDepFiles = fullDepFiles;
        this.apiDepFiles = apiDepFiles;
        this.ignore = ignore;
    }

    /**
     * @return Full list of artifacts that are required by the project
     */
    public Set<ResolvedArtifact> getAllRequiredArtifacts() {
        init();
        return allRequiredArtifacts;
    }

    /**
     * @return Artifacts used in project's APIs - public or protected methods.
     */
    public Set<ResolvedArtifact> getApiArtifacts() {
        return apiArtifacts;
    }

    /**
     * @return Artifacts that are required but are not directly declared by the project
     */
    public List<ResolvedArtifact> getImplicitDependencies() {
        init();
        return diffArtifacts(allRequiredArtifacts, declaredArtifacts);
    }

    /**
     * @return Artifacts that are declared, but not used.
     */
    public List<ResolvedArtifact> getUnusedDependencies() {
        init();
        return diffArtifacts(declaredArtifacts, allRequiredArtifacts);
    }

    private List<ResolvedArtifact> diffArtifacts(Set<ResolvedArtifact> base, Set<ResolvedArtifact> compare) {
        return Sets.difference(base, compare)
                .stream()
                .filter(artifact -> !shouldIgnore(artifact))
                .sorted(Comparator.comparing(artifact -> artifact.getId().getDisplayName()))
                .collect(Collectors.toList());
    }

    private void init() {
        if (inited) {
            return;
        }
        Set<ResolvedDependency> declaredDependencies = dependenciesConfigurations.stream()
                .map(Configuration::getResolvedConfiguration)
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .collect(Collectors.toSet());

        indexes.populateIndexes(declaredDependencies);

        allRequiredArtifacts = findReferencedArtifacts(fullDepFiles);
        apiArtifacts = findReferencedArtifacts(apiDepFiles);
        declaredArtifacts = declaredDependencies.stream()
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .filter(dependency -> VALID_ARTIFACT_EXTENSIONS
                        .contains(dependency.getExtension()))
                .collect(Collectors.toSet());
        inited = true;
        //clear the memory from the massive dependency map
        indexes.reset();
    }

    private Set<ResolvedArtifact> findReferencedArtifacts(FileCollection dotFiles) {
        return findReferencedClasses(dotFiles).stream()
                .map(indexes::classToDependency)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(x -> !isArtifactFromCurrentProject(x))
                .collect(Collectors.toSet());
    }

    private Set<String> findReferencedClasses(FileCollection dotFiles) {
        return Streams.stream(dotFiles.iterator())
                .filter(File::exists)
                .flatMap(this::findReferencedClasses)
                .collect(Collectors.toSet());
    }

    /**
     * Return all classes that are referenced (i.e. depended upon) by classes in the given dot file
     */
    private Stream<String> findReferencedClasses(File dotFile) {
        try (InputStream input = new FileInputStream(dotFile)) {
            GraphParser parser = new GraphParser(input);
            return parser.getEdges()
                    .values()
                    .stream()
                    .map(e -> e.getNode2().getId())
                    .map(this::cleanDependencyName);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to analyze " + dotFile, e);
        }
    }

    /**
     * Strips excess junk written by jdeps
     */
    private String cleanDependencyName(String name) {
        return name.replace(" (not found)", "").replace("\"", "");
    }

    /**
     * Return true if the resolved artifact is derived from a project in the current build rather than an
     * external jar.
     */
    private boolean isArtifactFromCurrentProject(ResolvedArtifact artifact) {
        if (!DependencyUtils.isProjectArtifact(artifact)) {
            return false;
        }
        return ((ProjectComponentIdentifier) artifact.getId().getComponentIdentifier()).getProjectPath()
                .equals(project.getPath());
    }

    private boolean shouldIgnore(ResolvedArtifact artifact) {
        return ignore.contains(DependencyUtils.getArtifactName(artifact));
    }

    @ThreadSafe
    public static final class Indexes {
        private final Map<String, ResolvedArtifact> classToDependency = new ConcurrentHashMap<>();

        public void populateIndexes(Set<ResolvedDependency> declaredDependencies) {
            Set<ResolvedArtifact> allArtifacts = declaredDependencies.stream()
                    .flatMap(dependency -> dependency.getAllModuleArtifacts().stream())
                    .filter(dependency -> VALID_ARTIFACT_EXTENSIONS.contains(dependency.getExtension()))
                    .collect(Collectors.toSet());

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
