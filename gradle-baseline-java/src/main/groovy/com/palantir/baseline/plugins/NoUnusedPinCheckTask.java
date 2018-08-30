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

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

public class NoUnusedPinCheckTask extends DefaultTask {

    private final File propsFile;

    @Inject
    public NoUnusedPinCheckTask(File propsFile) {
        this.propsFile = propsFile;
    }

    @Input
    public final Set<String> getResolvedArtifacts() {
        return BaselineVersions.getResolvedArtifacts(getProject());
    }

    @InputFile
    public final File getPropsFile() {
        return propsFile;
    }

    @TaskAction
    public final void checkNoUnusedPin() {
        Set<String> artifacts = getResolvedArtifacts();
        List<String> unusedProps = VersionsPropsReader.readVersionsProps(getPropsFile()).stream()
                .map(Pair::getLeft)
                .filter(propName -> {
                    String regex = propName.replaceAll("\\*", ".*");
                    return artifacts.stream().noneMatch(artifact -> artifact.matches(regex));
                })
                .collect(Collectors.toList());

        if (!unusedProps.isEmpty()) {
            String unusedPropsString = String.join("\n", unusedProps);
            throw new RuntimeException("There are unused pins in your versions.props: \n" + unusedPropsString);
        }
    }

}
