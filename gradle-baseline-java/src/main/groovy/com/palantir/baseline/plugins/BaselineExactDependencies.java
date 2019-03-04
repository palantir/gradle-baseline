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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.palantir.baseline.tasks.CheckUnusedDependenciesTask;
import com.palantir.baseline.tasks.CheckImplicitDependenciesTask;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.shared.dependency.analyzer.ClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public final class BaselineExactDependencies implements Plugin<Project> {

    public static final Indexes INDEXES = new Indexes();
    public static final DependencyAnalyzer CLASS_FILE_ANALYZER = new ASMDependencyAnalyzer();

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            Extension extension = project.getExtensions().create("exactDependencies", Extension.class, project);

            // this is liberally applied to ease the Java8 -> 11 transition
            extension.ignore("javax.annotation:javax.annotation-api");
            extension.ignore("org.slf4j:slf4j-api");

            SourceSetContainer sourceSets = project.getConvention()
                    .getPlugin(JavaPluginConvention.class).getSourceSets();
            SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            Configuration compileClasspath = project.getConfigurations()
                    .getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);

            project.getTasks().create("checkUnusedDependencies", CheckUnusedDependenciesTask.class, task -> {
                task.dependsOn(JavaPlugin.CLASSES_TASK_NAME);
                task.setClasses(mainSourceSet.getOutput().getClassesDirs());
                task.dependenciesConfiguration(compileClasspath);
                task.allowExtraneous(extension.ignored);
            });

            project.getTasks().create("checkImplicitDependencies", CheckImplicitDependenciesTask.class, task -> {
                task.dependsOn(JavaPlugin.CLASSES_TASK_NAME);
                task.setClasses(mainSourceSet.getOutput().getClassesDirs());
                task.dependenciesConfiguration(compileClasspath);
                task.allowExtraneous(extension.ignored);
            });

            // TODO(dfox): run these tasks as part of `./gradlew check`
        });
    }

    public static class Extension implements Serializable {
        private final SetProperty<String> ignored;

        @Inject
        public Extension(Project project) {
            this.ignored = project.getObjects().setProperty(String.class);
            this.ignored.set(Collections.emptyList());
        }

        public final void ignore(String artifact) {
            validate(artifact);
            ignored.add(artifact);
        }

        private static void validate(String artifact) {
            List<String> strings = Splitter.on(':').splitToList(artifact);
            Preconditions.checkState(strings.size() == 2, "Artifact must be of the form 'group:name'. Found:",
                    artifact);
        }
    }

    // TODO(dfox): make this class thread safe
    public static class Indexes {
        private static final ClassAnalyzer JAR_ANALYZER = new DefaultClassAnalyzer();

        private final Map<String, ResolvedArtifact> classToDependency = new HashMap<>();
        private final Map<ResolvedArtifact, Set<String>> classesFromArtifact = new HashMap<>();
        private final Map<ResolvedArtifact, ResolvedDependency> artifactsFromDependency = new HashMap<>();

        public void populateIndexes(Set<ResolvedDependency> declaredDependencies) {
            Set<ResolvedArtifact> allArtifacts = declaredDependencies.stream()
                    .flatMap(dependency -> dependency.getAllModuleArtifacts().stream())
                    .collect(Collectors.toSet());

            allArtifacts.forEach(artifact -> {
                try {
                    File jar = artifact.getFile();
                    Set<String> classesInArtifact = JAR_ANALYZER.analyze(jar.toURI().toURL());
                    classesFromArtifact.put(artifact, classesInArtifact);
                    classesInArtifact.forEach(clazz -> classToDependency.put(clazz, artifact));
                } catch (IOException e) {
                    throw new RuntimeException("Unable to analyze artifact", e);
                }
            });

            declaredDependencies.stream().forEach(dependency -> {
                Set<ResolvedArtifact> artifacts = dependency.getModuleArtifacts();
                artifacts.forEach(artifact -> artifactsFromDependency.put(artifact, dependency));
            });
        }

        /** Given a class, what dependency brought it in. */
        public Optional<ResolvedArtifact> classToDependency(String clazz) {
            return Optional.ofNullable(classToDependency.get(clazz));
        }


        /** Given an artifact, what classes does it contain. */
        public Stream<String> classesFromArtifact(ResolvedArtifact resolvedArtifact) {
            return Preconditions.checkNotNull(
                    classesFromArtifact.get(resolvedArtifact),
                    "Unable to find resolved artifact").stream();
        }

        public ResolvedDependency artifactsFromDependency(ResolvedArtifact resolvedArtifact) {
            return Preconditions.checkNotNull(
                    artifactsFromDependency.get(resolvedArtifact),
                    "Unable to find resolved artifact");
        }
    }
}
