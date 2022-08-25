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
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.palantir.baseline.tasks.CheckImplicitDependenciesParentTask;
import com.palantir.baseline.tasks.CheckImplicitDependenciesTask;
import com.palantir.baseline.tasks.CheckUnusedDependenciesParentTask;
import com.palantir.baseline.tasks.CheckUnusedDependenciesTask;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.dependency.analyzer.ClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;

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
            TaskProvider<CheckUnusedDependenciesParentTask> checkUnusedDependencies,
            TaskProvider<CheckImplicitDependenciesParentTask> checkImplicitDependencies) {
        Configuration implementation =
                project.getConfigurations().getByName(sourceSet.getImplementationConfigurationName());
        Optional<Configuration> maybeCompile =
                Optional.ofNullable(project.getConfigurations().findByName(getCompileConfigurationName(sourceSet)));
        Configuration compileClasspath =
                project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());

        Configuration explicitCompile = project.getConfigurations()
                .create("baseline-exact-dependencies-" + sourceSet.getName(), conf -> {
                    conf.setDescription(String.format(
                            "Tracks the explicit (not inherited) dependencies added to either %s "
                                    + "or compile (deprecated)",
                            implementation));
                    conf.setVisible(false);
                    conf.setCanBeConsumed(false);

                    conf.attributes(attributes -> {
                        // This ensures we resolve 'compile' variants rather than 'runtime'
                        // This is the same attribute that's being set on compileClasspath
                        attributes.attribute(
                                Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
                        // Ensure we resolve the classes directory for local projects where possible, rather than the
                        // 'jar' file. We can only do this on Gradle 5.6+, otherwise do nothing.
                        if (GradleVersion.current().compareTo(GradleVersion.version("5.6")) >= 0) {
                            attributes.attribute(
                                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                    project.getObjects().named(LibraryElements.class, LibraryElements.CLASSES));
                        }
                    });

                    // Without this, the 'checkUnusedDependencies correctly picks up project dependency on java-library'
                    // test fails, by not causing gradle run the jar task, but resolving the path to the jar (rather
                    // than to the classes directory), which then doesn't exist.

                    // Specifically, we need to pick up the LIBRARY_ELEMENTS_ATTRIBUTE, which is being configured on
                    // compileClasspath in JavaBasePlugin.defineConfigurationsForSourceSet, but we can't reference it
                    // directly because that would require us to depend on Gradle 5.6.
                    // Instead, we just copy the attributes from compileClasspath.
                    compileClasspath.getAttributes().keySet().forEach(attribute -> {
                        Object value = compileClasspath.getAttributes().getAttribute(attribute);
                        conf.getAttributes().attribute((Attribute<Object>) attribute, value);
                    });
                });

        // Figure out what our compile dependencies are while ignoring dependencies we've inherited from other source
        // sets. For example, if we are `test`, some of our configurations extend from the `main` source set:
        // testImplementation     extendsFrom(implementation)
        //  \-- testCompile       extendsFrom(compile)
        // We therefore want to look at only the dependencies _directly_ declared in the implementation and compile
        // configurations (belonging to our source set)
        project.afterEvaluate(p -> {
            Configuration implCopy = implementation.copy();
            // Without these, explicitCompile will successfully resolve 0 files and you'll waste 1 hour trying
            // to figure out why.
            project.getConfigurations().add(implCopy);

            explicitCompile.extendsFrom(implCopy);

            // For Gradle 6 and below, the compile configuration might still be used.
            maybeCompile.ifPresent(compile -> {
                Configuration compileCopy = compile.copy();
                // Ensure it's not resolvable, otherwise plugins that resolve all configurations might have
                // a bad time resolving this with GCV, if you have direct dependencies without corresponding entries in
                // versions.props, but instead rely on getting a version for them from the lock file.
                compileCopy.setCanBeResolved(false);
                compileCopy.setCanBeConsumed(false);

                project.getConfigurations().add(compileCopy);

                explicitCompile.extendsFrom(compileCopy);
            });
        });

        explicitCompile.withDependencies(deps -> {
            // Pick up GCV locks. We're making an internal assumption that this configuration exists,
            // but we can rely on this since we control GCV.
            // Alternatively, we could tell GCV to lock this configuration, at the cost of a slightly more
            // expensive 'unifiedClasspath' resolution during lock computation.
            if (project.getRootProject().getPluginManager().hasPlugin("com.palantir.versions-lock")) {
                explicitCompile.extendsFrom(project.getConfigurations().getByName("lockConstraints"));
            }
            // Inherit the excludes from compileClasspath too (that get aggregated from all its super-configurations).
            compileClasspath.getExcludeRules().forEach(rule -> explicitCompile.exclude(excludeRuleAsMap(rule)));
        });

        // Since we are copying configurations before resolving 'explicitCompile', make double sure that it's not
        // being resolved (or dependencies realized via `.getIncoming().getDependencies()`) too early.
        AtomicBoolean projectsEvaluated = new AtomicBoolean();
        project.getGradle().projectsEvaluated(g -> projectsEvaluated.set(true));
        explicitCompile
                .getIncoming()
                .beforeResolve(ir -> Preconditions.checkState(
                        projectsEvaluated.get()
                                || (project.getGradle().getStartParameter().isConfigureOnDemand()
                                        && project.getState().getExecuted()),
                        "Tried to resolve %s too early.",
                        explicitCompile));

        TaskProvider<CheckUnusedDependenciesTask> sourceSetUnusedDependencies = project.getTasks()
                .register(
                        checkUnusedDependenciesNameForSourceSet(sourceSet), CheckUnusedDependenciesTask.class, task -> {
                            task.dependsOn(sourceSet.getClassesTaskName());
                            task.setSourceClasses(sourceSet.getOutput().getClassesDirs());
                            task.dependenciesConfiguration(explicitCompile);

                            // this is liberally applied to ease the Java8 -> 11 transition
                            task.ignore("javax.annotation", "javax.annotation-api");

                            // this is typically used instead of junit-jupiter-api to simplify configuration
                            task.ignore("org.junit.jupiter", "junit-jupiter");

                            // pick up ignores configured globally on the parent task
                            task.ignore(checkUnusedDependencies.get().getIgnore());
                        });
        checkUnusedDependencies.configure(task -> task.dependsOn(sourceSetUnusedDependencies));
        TaskProvider<CheckImplicitDependenciesTask> sourceSetCheckImplicitDependencies = project.getTasks()
                .register(
                        "checkImplicitDependencies" + StringUtils.capitalize(sourceSet.getName()),
                        CheckImplicitDependenciesTask.class,
                        task -> {
                            task.dependsOn(sourceSet.getClassesTaskName());
                            task.setSourceClasses(sourceSet.getOutput().getClassesDirs());
                            task.dependenciesConfiguration(compileClasspath);
                            task.suggestionConfigurationName(sourceSet.getImplementationConfigurationName());

                            task.ignore("org.slf4j", "slf4j-api");

                            // pick up ignores configured globally on the parent task
                            task.ignore(checkImplicitDependencies.get().getIgnore());
                        });
        checkImplicitDependencies.configure(task -> task.dependsOn(sourceSetCheckImplicitDependencies));
    }

    static String checkUnusedDependenciesNameForSourceSet(SourceSet sourceSet) {
        return "checkUnusedDependencies" + StringUtils.capitalize(sourceSet.getName());
    }

    /**
     * The {@link SourceSet#getCompileConfigurationName()} method got removed in Gradle 7. Because we want to stay
     * compatible with Gradle 6 but can't compile this method, we reimplement it temporarily.
     * TODO(fwindheuser): Remove after dropping support for Gradle 6.
     */
    private static String getCompileConfigurationName(SourceSet sourceSet) {
        String baseName = sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)
                ? ""
                : GUtil.toCamelCase(sourceSet.getName());
        return StringUtils.uncapitalize(baseName + StringUtils.capitalize("compile"));
    }

    private static Map<String, String> excludeRuleAsMap(ExcludeRule rule) {
        // Both 'ExcludeRule#getGroup' and 'ExcludeRule#getModule' can return null.
        Builder<String, String> excludeRule = ImmutableMap.builder();
        if (rule.getGroup() != null) {
            excludeRule.put("group", rule.getGroup());
        }
        if (rule.getModule() != null) {
            excludeRule.put("module", rule.getModule());
        }
        return excludeRule.build();
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
        private final Map<String, Set<ResolvedArtifact>> classToDependency = new ConcurrentHashMap<>();
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
                    classesInArtifact.forEach(clazz -> classToDependency
                            .computeIfAbsent(clazz, _ignored -> ConcurrentHashMap.newKeySet())
                            .add(artifact));
                } catch (IOException e) {
                    throw new RuntimeException("Unable to analyze artifact", e);
                }
            });

            declaredDependencies.forEach(dependency -> dependency
                    .getModuleArtifacts()
                    .forEach(artifact -> artifactsFromDependency.put(artifact, dependency)));
        }

        /** Given a class, what dependency brought it in. */
        public Stream<ResolvedArtifact> classToArtifacts(String clazz) {
            return classToDependency.getOrDefault(clazz, ImmutableSet.of()).stream();
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

    public static String ignoreCoordinate(String group, String name) {
        return group + ":" + name;
    }
}
