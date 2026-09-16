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
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public abstract class CheckUnusedDependenciesTask extends DefaultTask {

    @Input
    @Option(option = "fix", description = "Remove unused dependency declarations from build.gradle")
    public abstract Property<Boolean> getFix();

    @Input
    protected abstract SetProperty<String> getDeclaredConfigurationNames();

    @Input
    public abstract SetProperty<String> getIgnored();

    @Classpath
    public abstract ListProperty<Configuration> getDependenciesConfigurations();

    @Input
    protected abstract SetProperty<ExplicitDependency> getExplicitDependencies();

    @Classpath
    public abstract ConfigurableFileCollection getSourceClasses();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    private final Usage consistentVersionsUsage;

    public CheckUnusedDependenciesTask() {
        setGroup("Verification");
        setDescription("Ensures no extraneous dependencies are declared");
        consistentVersionsUsage = getObjectFactory().newInstance(Usage.class, "consistent-versions-usage");
        getFix().convention(false);
        getOutputs().upToDateWhen(_task -> !getFix().get());
    }

    @TaskAction
    public final void checkUnusedDependencies() throws IOException {
        Set<ResolvedConfiguration> resolvedConfigurations = getDependenciesConfigurations().get().stream()
                .map(Configuration::getResolvedConfiguration)
                .collect(Collectors.toSet());
        BaselineExactDependencies.INDEXES.populateIndexes(resolvedConfigurations);

        Set<ExplicitDependency> explicitlyDeclaredDependencies =
                getExplicitDependencies().get();
        Set<ResolvedArtifact> declaredArtifacts = resolvedConfigurations.stream()
                .flatMap(resolved -> resolved.getFirstLevelModuleDependencies().stream())
                .filter(resolvedDependency -> resolvedDependency.getModuleArtifacts().stream()
                        .map(ExplicitDependency::from)
                        .anyMatch(explicitlyDeclaredDependencies::contains))
                .flatMap(dependency -> dependency.getModuleArtifacts().stream())
                .filter(dependency ->
                        BaselineExactDependencies.VALID_ARTIFACT_EXTENSIONS.contains(dependency.getExtension()))
                .collect(Collectors.toSet());

        Set<String> necessaryArtifactsDeclaration = Streams.stream(
                        getSourceClasses().iterator())
                .flatMap(BaselineExactDependencies::referencedClasses)
                .flatMap(BaselineExactDependencies.INDEXES::classToArtifacts)
                .map(BaselineExactDependencies::asString)
                .collect(Collectors.toSet());

        Set<ResolvedArtifact> possiblyUnusedArtifacts = declaredArtifacts.stream()
                .filter(artifact ->
                        !necessaryArtifactsDeclaration.contains(BaselineExactDependencies.asString(artifact)))
                .collect(Collectors.toSet());
        getLogger()
                .debug(
                        "Possibly unused dependencies: {}",
                        possiblyUnusedArtifacts.stream()
                                .map(BaselineExactDependencies::asString)
                                .sorted()
                                .collect(Collectors.toList()));
        List<ResolvedArtifact> unusedArtifacts = possiblyUnusedArtifacts.stream()
                .filter(artifact -> !shouldIgnore(artifact))
                .sorted(Comparator.comparing(BaselineExactDependencies::asString))
                .toList();
        if (getFix().get() && !unusedArtifacts.isEmpty()) {
            unusedArtifacts = removeUnusedDependencies(unusedArtifacts);
        }
        if (!unusedArtifacts.isEmpty()) {
            // TODO(dfox): don't print warnings for jars that define service loaded classes (e.g. meta-inf)
            StringBuilder builder = new StringBuilder();
            builder.append(String.format(
                    "Found %s dependencies unused during compilation, please delete them from '%s':",
                    unusedArtifacts.size(), buildFile()));
            for (ResolvedArtifact resolvedArtifact : unusedArtifacts) {
                builder.append("\n\t").append(BaselineExactDependencies.asDependencyStringWithName(resolvedArtifact));
            }
            throw new ExceptionWithSuggestion(builder.toString(), buildFile().toString());
        }
    }

    @SuppressWarnings("for-rollout:IllegalMethodCalledDuringTaskExecution")
    private List<ResolvedArtifact> removeUnusedDependencies(List<ResolvedArtifact> unusedArtifacts) throws IOException {
        Path buildFilePath = getProject().getBuildFile().toPath();
        if (!buildFilePath.toString().endsWith(".gradle") || !Files.isRegularFile(buildFilePath)) {
            return unusedArtifacts;
        }
        String originalContents = Files.readString(buildFilePath);
        String updatedContents = originalContents;
        String configurationPattern = getDeclaredConfigurationNames().get().stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        List<ResolvedArtifact> remainingArtifacts = new ArrayList<>();
        for (ResolvedArtifact artifact : unusedArtifacts) {
            if (Optional.ofNullable(artifact.getClassifier()).isPresent()
                    || artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) {
                remainingArtifacts.add(artifact);
                continue;
            }
            String dependencyCoordinates = BaselineExactDependencies.asString(artifact);
            Matcher matcher = dependencyDeclarationPattern(configurationPattern, dependencyCoordinates)
                    .matcher(updatedContents);
            if (!matcher.find()) {
                remainingArtifacts.add(artifact);
                continue;
            }
            updatedContents = matcher.replaceAll("");
            getLogger().lifecycle("Removed unused dependency {} from {}", dependencyCoordinates, buildFile());
        }
        if (!updatedContents.equals(originalContents)) {
            Files.writeString(buildFilePath, updatedContents);
        }
        return remainingArtifacts;
    }

    private static Pattern dependencyDeclarationPattern(String configurationPattern, String dependencyCoordinates) {
        // Support only standalone Groovy string declarations, with an optional literal version and line comment.
        // Example: implementation 'org.freemarker:freemarker:2.3.34' // unused
        return Pattern.compile("(?m)^[\\t ]*(?:" + configurationPattern + ")[\\t ]+(['\"])"
                + Pattern.quote(dependencyCoordinates)
                + "(?::[a-zA-Z0-9._+\\-]+)?\\1[\\t ]*;?[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|\\z)");
    }

    @SuppressWarnings("for-rollout:IllegalMethodCalledDuringTaskExecution")
    private Path buildFile() {
        return getProject()
                .getRootDir()
                .toPath()
                .relativize(getProject().getBuildFile().toPath());
    }

    private boolean isNotGcvDependency(ModuleDependency dependency) {
        return !consistentVersionsUsage.equals(dependency.getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE));
    }

    private boolean shouldIgnore(ResolvedArtifact artifact) {
        return getIgnored().get().contains(BaselineExactDependencies.asString(artifact));
    }

    public final void dependenciesConfiguration(Configuration dependenciesConfiguration) {
        getDependenciesConfigurations().add(Objects.requireNonNull(dependenciesConfiguration));
    }

    public final void ignore(Provider<Set<String>> value) {
        getIgnored().addAll(value);
    }

    public final void ignore(String group, String name) {
        getIgnored().add(BaselineExactDependencies.ignoreCoordinate(group, name));
    }

    public final void withDeclaredDependenciesFrom(Provider<Configuration> configuration) {
        getDeclaredConfigurationNames().add(configuration.map(Configuration::getName));
        Predicate<ModuleDependency> isNotGcvDependency = this::isNotGcvDependency;
        getExplicitDependencies().addAll(configuration.map(conf -> new Iterable<ExplicitDependency>() {
            @Override
            public @NotNull Iterator<ExplicitDependency> iterator() {
                return conf.getDependencies().withType(ModuleDependency.class).stream()
                        .filter(isNotGcvDependency)
                        .flatMap(ExplicitDependency::from)
                        .iterator();
            }
        }));
    }

    // public because it needs to be able to be accessed by Gradle when Configuration Cache is enabled.
    public record ExplicitDependency(
            String group,
            String name,
            @Nullable String classifier,
            @Nullable String extension) implements Serializable {

        private static Stream<ExplicitDependency> from(ModuleDependency dependency) {
            if (!dependency.getArtifacts().isEmpty()) {
                return dependency.getArtifacts().stream()
                        .map(artifact -> new ExplicitDependency(
                                dependency.getGroup(),
                                dependency.getName(),
                                artifact.getClassifier(),
                                artifact.getExtension()));
            }

            return Stream.of(
                    new ExplicitDependency(
                            dependency.getGroup(), dependency.getName(), null, DependencyArtifact.DEFAULT_TYPE),
                    new ExplicitDependency(dependency.getGroup(), dependency.getName(), null, ""));
        }

        private static ExplicitDependency from(ResolvedArtifact resolvedArtifact) {
            ModuleVersionIdentifier id = resolvedArtifact.getModuleVersion().getId();
            return new ExplicitDependency(
                    id.getGroup(), id.getName(), resolvedArtifact.getClassifier(), resolvedArtifact.getExtension());
        }
    }
}
