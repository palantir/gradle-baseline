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

package com.palantir.baseline.plugins;

import com.google.common.collect.ImmutableSet;
import com.palantir.baseline.tasks.CheckImplicitDependenciesParentTask;
import com.palantir.baseline.tasks.CheckImplicitDependenciesTask;
import com.palantir.baseline.tasks.CheckUnusedDependenciesParentTask;
import com.palantir.baseline.tasks.CheckUnusedDependenciesTask;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.dependency.analyzer.ClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/** Validates that java projects declare exactly the dependencies they rely on, no more and no less. */
public final class BaselineExactDependencies implements Plugin<Project> {

    private static final ClassAnalyzer JAR_ANALYZER = new DefaultClassAnalyzer();
    private static final DependencyAnalyzer CLASS_FILE_ANALYZER = new ASMDependencyAnalyzer();

    // All applications of this plugin share a single static 'Indexes' instance, because the classes
    // contained in a particular jar are immutable.
    public static final Indexes INDEXES = new Indexes();
    public static final ImmutableSet<String> VALID_ARTIFACT_EXTENSIONS = ImmutableSet.of("jar", "");

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            TaskProvider<CheckUnusedDependenciesParentTask> checkUnusedDependencies =
                    project.getTasks().register("checkUnusedDependencies", CheckUnusedDependenciesParentTask.class);
            TaskProvider<CheckImplicitDependenciesParentTask> checkImplicitDependencies =
                    project.getTasks().register("checkImplicitDependencies", CheckImplicitDependenciesParentTask.class);

            project.getExtensions()
                    .getByType(JavaPluginExtension.class)
                    .getSourceSets()
                    .configureEach(sourceSet ->
                            configureSourceSet(project, sourceSet, checkUnusedDependencies, checkImplicitDependencies));
        });
    }

        private static void configureSourceSet(
            Project project,
            SourceSet sourceSet,
            TaskProvider<CheckUnusedDependenciesParentTask> checkUnusedDependencies,
            TaskProvider<CheckImplicitDependenciesParentTask> checkImplicitDependencies) {
        NamedDomainObjectProvider<Configuration> implementation =
                project.getConfigurations().named(sourceSet.getImplementationConfigurationName());
        NamedDomainObjectProvider<Configuration> compileClasspath =
                project.getConfigurations().named(sourceSet.getCompileClasspathConfigurationName());

                TaskProvider<CheckUnusedDependenciesTask> sourceSetUnusedDependencies = project.getTasks()
                .register(
                        checkUnusedDependenciesNameForSourceSet(sourceSet), CheckUnusedDependenciesTask.class, task -> {
                            task.dependsOn(sourceSet.getClassesTaskName());
                            task.getSourceClasses()
                                    .setFrom(sourceSet.getOutput().getClassesDirs());
                            task.getDependenciesConfigurations().add(compileClasspath);
                            task.withDeclaredDependenciesFrom(implementation);

                            // ignore intra-project dependencies, which are typically added automatically for things
                            // like test fixtures
                            task.ignore(project.provider(() ->
                                    Set.of(ignoreCoordinate(project.getGroup().toString(), project.getName()))));

                            // this is liberally applied to ease the Java8 -> 11 transition
                            task.ignore("javax.annotation", "javax.annotation-api");

                            // this is typically used instead of junit-jupiter-api to simplify configuration
                            task.ignore("org.junit.jupiter", "junit-jupiter");

                            // pick up ignores configured globally on the parent task
                            task.ignore(checkUnusedDependencies.flatMap(CheckUnusedDependenciesParentTask::getIgnore));
                        });
        checkUnusedDependencies.configure(task -> task.dependsOn(sourceSetUnusedDependencies));
                TaskProvider<CheckImplicitDependenciesTask> sourceSetCheckImplicitDependencies = project.getTasks()
                .register(
                        "checkImplicitDependencies" + StringUtils.capitalize(sourceSet.getName()),
                        CheckImplicitDependenciesTask.class,
                        task -> {
                            task.dependsOn(sourceSet.getClassesTaskName());
                            task.setSourceClasses(sourceSet.getOutput().getClassesDirs());
                            task.getDependenciesConfigurations().add(compileClasspath);
                            task.suggestionConfigurationName(sourceSet.getImplementationConfigurationName());

                            task.ignore("org.slf4j", "slf4j-api");

                            // pick up ignores configured globally on the parent task
                            task.ignore(
                                    checkImplicitDependencies.flatMap(CheckImplicitDependenciesParentTask::getIgnore));
                        });
        checkImplicitDependencies.configure(task -> task.dependsOn(sourceSetCheckImplicitDependencies));
    }

    static String checkUnusedDependenciesNameForSourceSet(SourceSet sourceSet) {
        return "checkUnusedDependencies" + StringUtils.capitalize(sourceSet.getName());
    }

    /** Given a {@code com/palantir/product/Foo.class} file, what other classes does it import/reference. */
    public static Stream<String> referencedClasses(File classFile) {
        try {
            return BaselineExactDependencies.CLASS_FILE_ANALYZER
                    .analyze(classFile.toURI().toURL())
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to analyze " + classFile, e);
        }
    }

    public static String asString(ResolvedArtifact artifact) {
        ModuleVersionIdentifier moduleVersionId = artifact.getModuleVersion().getId();
        StringBuilder builder = new StringBuilder()
                .append(moduleVersionId.getGroup())
                .append(":")
                .append(moduleVersionId.getName());
        if (artifact.getClassifier() != null) {
            builder.append("::").append(artifact.getClassifier());
        }
        return builder.toString();
    }

    public static String asDependencyStringWithName(ResolvedArtifact artifact) {
        ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
        if (componentId instanceof ProjectComponentIdentifier projectComponentId) {
            return "project('%s') <-- %s".formatted(projectComponentId.getProjectPath(), artifact.getName());
        }

        return asString(artifact);
    }

    @ThreadSafe
    public static final class Indexes {
        private final Map<String, Set<ResolvedArtifact>> classToArtifacts = new ConcurrentHashMap<>();

        public void populateIndexes(Set<ResolvedConfiguration> configurations) {
            configurations.stream()
                    .flatMap(configuration -> configuration.getResolvedArtifacts().stream())
                    .filter(artifact -> VALID_ARTIFACT_EXTENSIONS.contains(artifact.getExtension()))
                    .forEach(artifact -> {
                        try {
                            File jar = artifact.getFile();
                            Set<String> classesInArtifact =
                                    JAR_ANALYZER.analyze(jar.toURI().toURL());
                            classesInArtifact.forEach(clazz -> classToArtifacts
                                    .computeIfAbsent(clazz, _ignored -> ConcurrentHashMap.newKeySet())
                                    .add(artifact));
                        } catch (IOException e) {
                            throw new UncheckedIOException("Unable to analyze artifact", e);
                        }
                    });
        }

        /** Given a class, what dependency brought it in. */
        public Stream<ResolvedArtifact> classToArtifacts(String clazz) {
            return classToArtifacts.getOrDefault(clazz, ImmutableSet.of()).stream();
        }
    }

    public static String ignoreCoordinate(String group, String name) {
        return group + ":" + name;
    }
}
