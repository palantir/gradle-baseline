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

import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class ExtractRefasterTask extends DefaultTask {
    private final Property<Configuration> refasterSources = getProject().getObjects().property(Configuration.class);
    private final DirectoryProperty outputDir = getProject().getObjects().directoryProperty();

    @InputFiles
    public final Property<Configuration> getRefasterSources() {
        return refasterSources;
    }

    @OutputDirectory
    public final DirectoryProperty getOutputDir() {
        return outputDir;
    }

    @TaskAction
    public final void extract() {
        // Extract Java sources
        getRefasterSources().get().getResolvedConfiguration()
                .getFirstLevelModuleDependencies()
                .stream()
                .flatMap(dep -> dep.getModuleArtifacts().stream())
                .map(ResolvedArtifact::getFile)
                .flatMap(file -> {
                    if (file.getName().endsWith(".jar")) {
                        return getProject().zipTree(file).getFiles().stream()
                                .filter(zipFile -> zipFile.getName().endsWith(".java"));
                    } else if (file.getName().endsWith(".java")) {
                        return Stream.of(file);
                    } else {
                        getLogger().warn("Skipping refaster rule: {}", file);
                        return Stream.empty();
                    }
                })
                .forEach(file -> {
                    getProject().copy(copySpec -> {
                        copySpec.from(file);
                        copySpec.into(outputDir);
                    });
                });
    }
}
