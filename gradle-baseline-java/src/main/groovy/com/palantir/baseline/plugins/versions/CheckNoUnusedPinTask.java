/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins.versions;

import com.palantir.baseline.util.VersionsProps;
import com.palantir.baseline.util.VersionsProps.ParsedVersionsProps;
import com.palantir.baseline.util.VersionsProps.VersionForce;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class CheckNoUnusedPinTask extends DefaultTask {

    private final Property<Boolean> shouldFix = getProject().getObjects().property(Boolean.class);
    private final RegularFileProperty propsFileProperty = newInputFile();

    public CheckNoUnusedPinTask() {
        shouldFix.set(false);
        setGroup(BaselineVersions.GROUP);
        setDescription("Ensures all versions in your versions.props correspond to an actual gradle dependency");
    }

    final void setPropsFile(File propsFile) {
        this.propsFileProperty.set(propsFile);
    }

    @Input
    public final Set<String> getResolvedArtifacts() {
        return BaselineVersions.getAllProjectsResolvedArtifacts(getProject());
    }

    @InputFile
    public final Provider<RegularFile> getPropsFile() {
        return propsFileProperty;
    }

    @Option(option = "fix", description = "Whether to apply the suggested fix to versions.props")
    public final void setShouldFix(Provider<Boolean> shouldFix) {
        this.shouldFix.set(shouldFix);
    }

    @TaskAction
    public final void checkNoUnusedPin() {
        Set<String> artifacts = getResolvedArtifacts();
        ParsedVersionsProps parsedVersionsProps = VersionsProps.readVersionsProps(getPropsFile().get().getAsFile());
        List<String> unusedForces = parsedVersionsProps.forces()
                .stream()
                .map(VersionForce::name)
                .filter(propName -> {
                    String regex = propName.replaceAll("\\*", ".*");
                    return artifacts.stream().noneMatch(artifact -> artifact.matches(regex));
                })
                .collect(Collectors.toList());

        if (unusedForces.isEmpty()) {
            return;
        }

        if (shouldFix.get()) {
            getProject().getLogger().lifecycle("Removing unused pins from versions.props:\n"
                    + unusedForces.stream()
                    .map(name -> String.format(" - '%s'", name))
                    .collect(Collectors.joining("\n")));
            VersionsProps.writeVersionsProps(parsedVersionsProps, unusedForces, getPropsFile().get().getAsFile());
            return;
        }

        throw new RuntimeException(
                "There are unused pins in your versions.props: \n" + unusedForces
                        + "\n\n"
                        + "Rerun with --fix to remove them.");
    }

}
