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

package com.palantir.baseline.plugins;

import com.palantir.baseline.plugins.VersionsPropsReader.ParsedVersionsProps;
import com.palantir.baseline.plugins.VersionsPropsReader.VersionForce;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class NoUnusedPinCheckTask extends DefaultTask {

    private final File propsFile;
    private boolean fix = false;

    @Inject
    public NoUnusedPinCheckTask(File propsFile) {
        this.propsFile = propsFile;
    }

    @Input
    public final Set<String> getResolvedArtifacts() {
        return BaselineVersions.getAllProjectsResolvedArtifacts(getProject());
    }

    @InputFile
    public final File getPropsFile() {
        return propsFile;
    }

    @Option(option = "fix", description = "Whether to apply the suggested fix to versions.props")
    public final void setFix(boolean fix) {
        this.fix = fix;
    }

    @TaskAction
    public final void checkNoUnusedPin() {
        Set<String> artifacts = getResolvedArtifacts();
        ParsedVersionsProps parsedVersionsProps = VersionsPropsReader.readVersionsProps(getPropsFile());
        List<String> unusedForces = parsedVersionsProps.forces()
                .stream()
                .map(VersionForce::name)
                .filter(propName -> {
                    String regex = propName.replaceAll("\\*", ".*");
                    return artifacts.stream().noneMatch(artifact -> artifact.matches(regex));
                })
                .collect(Collectors.toList());

        if (!unusedForces.isEmpty()) {
            if (fix) {
                List<String> lines = parsedVersionsProps.lines();
                Set<Integer> indicesToSkip = unusedForces
                        .stream()
                        .map(parsedVersionsProps.namesToLocationMap()::get)
                        .collect(Collectors.toSet());
                try (BufferedWriter writer0 = Files.newBufferedWriter(
                        propsFile.toPath(), StandardOpenOption.TRUNCATE_EXISTING);
                     PrintWriter writer = new PrintWriter(writer0)) {
                    for (int index = 0; index < lines.size(); index++) {
                        if (!indicesToSkip.contains(index)) {
                            writer.println(lines.get(index));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(
                        "There are unused pins in your versions.props: \n" + unusedForces
                                + "\n\n"
                                + "Rerun with --fix to remove them.");
            }
        }
    }

}
