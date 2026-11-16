/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.baseline.classuniqueness;

import com.palantir.baseline.classuniqueness.ConflictFinder.Jar;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.util.streamex.StreamEx;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** The jars of one configuration whose classes are checked for uniqueness. */
public abstract class CheckedConfiguration {
    @Input
    public abstract Property<String> getConfigurationName();

    /** The {@link ClassIndex} of each jar, in classpath order. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getClassIndexes();

    /**
     * How the lock file shows each jar, such as {@code com.google.guava:guava} or {@code g:bar (classifier=tests)}, at
     * the same position as the jar's index in {@link #getClassIndexes()}. The two are separate lists because Gradle
     * tracks the indexes' content and the labels' values, and labels can't be keyed by file name: two jars can share
     * one.
     */
    @Input
    public abstract ListProperty<JarLabel> getJarLabels();

    public final void from(Configuration configuration) {
        // File dependencies are left out: they have no coordinates to show in the lock file.
        ArtifactCollection indexes = configuration
                .getIncoming()
                .artifactView(view -> {
                    view.attributes(attributes -> attributes.attribute(
                            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ClassIndexTransform.ARTIFACT_TYPE));
                    view.componentFilter(component -> component instanceof ModuleComponentIdentifier
                            || component instanceof ProjectComponentIdentifier);
                })
                .getArtifacts();
        ResolutionResult resolutionResult = configuration.getIncoming().getResolutionResult();
        // getAllComponents() resolves the graph when called, so it's called once the root component is needed
        Provider<Map<ComponentIdentifier, ModuleVersionIdentifier>> coordinates = resolutionResult
                .getRootComponent()
                .map(_root -> StreamEx.of(resolutionResult.getAllComponents())
                        .filter(component -> component.getModuleVersion() != null)
                        .toMap(ResolvedComponentResult::getId, ResolvedComponentResult::getModuleVersion));

        getConfigurationName().set(configuration.getName());
        getClassIndexes().from(indexes.getArtifactFiles());
        getJarLabels().set(indexes.getResolvedArtifacts().zip(coordinates, CheckedConfiguration::labels));
    }

    /** Loads the jars' indexes, minus the class names. */
    public final List<Jar> loadJars() throws IOException {
        List<File> indexFiles = List.copyOf(getClassIndexes().getFiles());
        List<JarLabel> labels = getJarLabels().get();
        if (indexFiles.size() != labels.size()) {
            throw new IllegalStateException(String.format(
                    "Expected a label for each of the %d class indexes of %s, but found %d",
                    indexFiles.size(), getConfigurationName().get(), labels.size()));
        }
        List<Jar> jars = new ArrayList<>(indexFiles.size());
        for (int i = 0; i < indexFiles.size(); i++) {
            jars.add(new Jar(labels.get(i), ClassIndex.read(indexFiles.get(i).toPath())));
        }
        return jars;
    }

    private static List<JarLabel> labels(
            Set<ResolvedArtifactResult> artifacts, Map<ComponentIdentifier, ModuleVersionIdentifier> coordinates) {
        return artifacts.stream()
                .map(artifact -> {
                    String jarName = ClassIndexTransform.jarFileName(artifact.getFile());
                    ModuleVersionIdentifier jarCoordinates =
                            coordinates.get(artifact.getId().getComponentIdentifier());
                    // Gradle allows a component to have no module version (getModuleVersion() is nullable), though
                    // modules and projects have one. Such a jar is shown by its file name.
                    return jarCoordinates == null ? new JarLabel(jarName) : JarLabel.of(jarCoordinates, jarName);
                })
                .toList();
    }
}
