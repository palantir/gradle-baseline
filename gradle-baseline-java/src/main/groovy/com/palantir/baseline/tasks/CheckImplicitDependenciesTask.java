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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

public class CheckImplicitDependenciesTask extends DefaultTask {

    private final ListProperty<Configuration> dependenciesConfigurations;
    private final Property<FileCollection> sourceClasses;
    private final SetProperty<String> ignore;

    public CheckImplicitDependenciesTask() {
        setGroup("Verification");
        setDescription("Ensures all dependencies are explicitly declared, not just transitively provided");
        dependenciesConfigurations = getProject().getObjects().listProperty(Configuration.class);
        dependenciesConfigurations.set(Collections.emptyList());
        sourceClasses = getProject().getObjects().property(FileCollection.class);
        ignore = getProject().getObjects().setProperty(String.class);
        ignore.set(Collections.emptySet());
    }

    @TaskAction
    public final void checkImplicitDependencies() {
        ImplicitDependenciesAnalyzer analyzer = new ImplicitDependenciesAnalyzer(getProject(),
                dependenciesConfigurations.get(), sourceClasses.get(), ignore.get());

        List<ResolvedArtifact> implicitDependencies = analyzer.findImplicitDependencies();

        if (!implicitDependencies.isEmpty()) {
            String suggestion = implicitDependencies.stream()
                    .map(artifact -> String.format("        implementation %s", analyzer.getArtifactNameRep(artifact)))
                    .sorted()
                    .collect(Collectors.joining("\n", "    dependencies {\n", "\n    }"));

            throw new GradleException(
                    String.format("Found %d implicit dependencies - consider adding the following explicit "
                            + "dependencies to '%s', or avoid using classes from these jars:\n%s",
                    implicitDependencies.size(),
                    buildFile(),
                    suggestion));
        }
    }

    private Path buildFile() {
        return getProject().getRootDir().toPath().relativize(getProject().getBuildFile().toPath());
    }

    @Input
    public final Provider<List<Configuration>> getDependenciesConfigurations() {
        return dependenciesConfigurations;
    }

    public final void dependenciesConfiguration(Configuration dependenciesConfiguration) {
        this.dependenciesConfigurations.add(Objects.requireNonNull(dependenciesConfiguration));
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
