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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.palantir.baseline.tasks.CheckImplicitDependenciesTask;
import com.palantir.baseline.tasks.CheckUnusedDependenciesTask;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.maven.shared.dependency.analyzer.ClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GUtil;

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
            TaskProvider<Task> checkUnusedDependencies = project.getTasks().register("checkUnusedDependencies");
            TaskProvider<Task> checkImplicitDependencies = project.getTasks().register("checkImplicitDependencies");

            project.getConvention()
                    .getPlugin(JavaPluginConvention.class)
                    .getSourceSets()
                    .all(sourceSet ->
                            configureSourceSet(project, sourceSet, checkUnusedDependencies, checkImplicitDependencies));
        });
    }

    private static void configureSourceSet(
            Project project,
            SourceSet sourceSet,
            TaskProvider<Task> checkUnusedDependencies,
            TaskProvider<Task> checkImplicitDependencies) {
        Configuration implementation =
                project.getConfigurations().getByName(sourceSet.getImplementationConfigurationName());
        Configuration compile = project.getConfigurations().getByName(sourceSet.getCompileConfigurationName());
        Configuration compileClasspath =
                project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());

        Configuration explicitCompile = project.getConfigurations()
                .create("baseline-exact-dependencies-" + sourceSet.getName(), conf -> {
                    conf.setDescription(String.format(
                            "Tracks the explicit (not inherited) dependencies added to either %s or %s",
                            compile.toString(), implementation.toString()));
                    conf.setVisible(false);
                    conf.setCanBeConsumed(false);
                    // Important! this ensures we resolve 'compile' variants rather than 'runtime'
                    // This is the same attribute that's being set on compileClasspath
                    conf.getAttributes()
                            .attribute(
                                    Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
                });

        // Figure out what our compile dependencies are while ignoring dependencies we've inherited from other source
        // sets. For example, if we are `test`, some of our configurations extend from the `main` source set:
        // testImplementation     extendsFrom(implementation)
        //  \-- testCompile       extendsFrom(compile)
        // We therefore want to look at only the dependencies _directly_ declared in the implementation and compile
        // configurations (belonging to our source set)
        project.afterEvaluate(p -> {
            Configuration implCopy = implementation.copy();
            Configuration compileCopy = compile.copy();
            explicitCompile.extendsFrom(implCopy, compileCopy);
            // Mirror the transitive constraints form compileClasspath in order to pick up GCV locks.
            // We should really do this with an addAllLater but that would require Gradle 6, or a hacky workaround.
            explicitCompile.getDependencyConstraints().addAll(compileClasspath.getAllDependencyConstraints());
            // Inherit the excludes from compileClasspath too (that get aggregated from all its super-configurations).
            compileClasspath
                    .getExcludeRules()
                    .forEach(rule -> explicitCompile.exclude(ImmutableMap.of(
                            "group", rule.getGroup(),
                            "module", rule.getModule())));
        });

        TaskProvider<CheckUnusedDependenciesTask> sourceSetUnusedDependencies = project.getTasks()
                .register(
                        GUtil.toLowerCamelCase("checkUnusedDependencies " + sourceSet.getName()),
                        CheckUnusedDependenciesTask.class,
                        task -> {
                            task.dependsOn(sourceSet.getClassesTaskName());
                            task.setSourceClasses(sourceSet.getOutput().getClassesDirs());
                            task.dependenciesConfiguration(explicitCompile);

                            // this is liberally applied to ease the Java8 -> 11 transition
                            task.ignore("javax.annotation", "javax.annotation-api");
                        });
        checkUnusedDependencies.configure(task -> task.dependsOn(sourceSetUnusedDependencies));
        TaskProvider<CheckImplicitDependenciesTask> sourceSetCheckImplicitDependencies = project.getTasks()
                .register(
                        GUtil.toLowerCamelCase("checkImplicitDependencies " + sourceSet.getName()),
                        CheckImplicitDependenciesTask.class,
                        task -> {
                            task.dependsOn(sourceSet.getClassesTaskName());
                            task.setSourceClasses(sourceSet.getOutput().getClassesDirs());
                            task.dependenciesConfiguration(compileClasspath);

                            task.ignore("org.slf4j", "slf4j-api");
                        });
        checkImplicitDependencies.configure(task -> task.dependsOn(sourceSetCheckImplicitDependencies));
    }

    private static Configuration makeInternalCompileConfiguration(Project project, Configuration compileOnly) {
        return project.getConfigurations().create("baseline-exact-dependencies-" + compileOnly.getName(), conf -> {
            conf.setVisible(false);
            conf.setCanBeConsumed(false);
            conf.extendsFrom(compileOnly);
            // Important! this ensures we resolve 'compile' variants rather than 'runtime'
            // This is the same attribute that's being set on compileClasspath
            conf.getAttributes()
                    .attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
        });
    }

    /** Given a {@code com/palantir/product/Foo.class} file, what other classes does it import/reference. */
    public static Stream<String> referencedClasses(File classFile) {
        try {
            return BaselineExactDependencies.CLASS_FILE_ANALYZER
                    .analyze(classFile.toURI().toURL())
                    .stream();
        } catch (IOException e) {
            throw new RuntimeException("Unable to analyze " + classFile, e);
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
        return asDependencyString(artifact, true);
    }

    public static String asDependencyStringWithoutName(ResolvedArtifact artifact) {
        return asDependencyString(artifact, false);
    }

    private static String asDependencyString(ResolvedArtifact artifact, boolean withName) {
        ComponentIdentifier componentId = artifact.getId().getComponentIdentifier();
        if (componentId instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifier projectComponentId = (ProjectComponentIdentifier) componentId;
            StringBuilder builder = new StringBuilder()
                    .append("project('")
                    .append(projectComponentId.getProjectPath())
                    .append("')");
            if (withName) {
                builder.append(" (").append(artifact.getId().getDisplayName()).append(")");
            }
            return builder.toString();
        }

        return asString(artifact);
    }

    @ThreadSafe
    public static final class Indexes {
        private final Map<String, ResolvedArtifact> classToDependency = new ConcurrentHashMap<>();
        private final Map<ResolvedArtifact, Set<String>> classesFromArtifact = new ConcurrentHashMap<>();
        private final Map<ResolvedArtifact, ResolvedDependency> artifactsFromDependency = new ConcurrentHashMap<>();

        public void populateIndexes(Set<ResolvedDependency> declaredDependencies) {
            Set<ResolvedArtifact> allArtifacts = declaredDependencies.stream()
                    .flatMap(dependency -> dependency.getAllModuleArtifacts().stream())
                    .filter(dependency -> VALID_ARTIFACT_EXTENSIONS.contains(dependency.getExtension()))
                    .collect(Collectors.toSet());

            allArtifacts.forEach(artifact -> {
                try {
                    File jar = artifact.getFile();
                    Set<String> classesInArtifact =
                            JAR_ANALYZER.analyze(jar.toURI().toURL());
                    classesFromArtifact.put(artifact, classesInArtifact);
                    classesInArtifact.forEach(clazz -> classToDependency.put(clazz, artifact));
                } catch (IOException e) {
                    throw new RuntimeException("Unable to analyze artifact", e);
                }
            });

            declaredDependencies.forEach(dependency -> dependency
                    .getModuleArtifacts()
                    .forEach(artifact -> artifactsFromDependency.put(artifact, dependency)));
        }

        /** Given a class, what dependency brought it in. */
        public Optional<ResolvedArtifact> classToDependency(String clazz) {
            return Optional.ofNullable(classToDependency.get(clazz));
        }

        /** Given an artifact, what classes does it contain. */
        public Stream<String> classesFromArtifact(ResolvedArtifact resolvedArtifact) {
            return Preconditions.checkNotNull(
                    classesFromArtifact.get(resolvedArtifact), "Unable to find resolved artifact")
                    .stream();
        }

        public ResolvedDependency artifactsFromDependency(ResolvedArtifact resolvedArtifact) {
            return Preconditions.checkNotNull(
                    artifactsFromDependency.get(resolvedArtifact), "Unable to find resolved artifact");
        }
    }
}
