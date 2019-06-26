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

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.compile.JavaCompile;

public class RefasterCompileTask extends JavaCompile {

    private final ConfigurableFileCollection refasterSources = getProject().files();
    private final RegularFileProperty refasterRulesFile = newInputFile();

    public RefasterCompileTask() {
        // Don't care about .class files
        setDestinationDir(getTemporaryDir());

        // Ensure we hit the non-incremental code-path since we override it
        getOptions().setIncremental(false);

        getOutputs().files(refasterRulesFile);
    }

    @Override
    protected final void compile() {
        // Clear out the default error-prone providers
        getOptions().getCompilerArgumentProviders().clear();
        getOptions().setCompilerArgs(ImmutableList.of(
                "-Xplugin:BaselineRefasterCompiler --out " + refasterRulesFile.get().getAsFile().getAbsolutePath()));

        // Extract Java sources
        List<File> javaSources = ImmutableList.copyOf(getRefasterSources()).stream()
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
                .collect(Collectors.toList());

        setSource(javaSources);
        super.compile();
    }

    public final ConfigurableFileCollection getRefasterSources() {
        return refasterSources;
    }

    public final RegularFileProperty getRefasterRulesFile() {
        return refasterRulesFile;
    }
}
