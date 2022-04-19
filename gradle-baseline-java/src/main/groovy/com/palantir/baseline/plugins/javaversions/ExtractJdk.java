/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTransform
public abstract class ExtractJdk implements TransformAction<TransformParameters.None> {
    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getZippedAzulJdk();

    @Inject
    public abstract FileSystemOperations getFileSystemOperations();

    @Inject
    public abstract ArchiveOperations getArchiveOperations();

    @Override
    public final void transform(TransformOutputs transformOutputs) {
        File tempDir = createTempDir();
        getFileSystemOperations().copy(copy -> {
            copy.from(getArchiveOperations().zipTree(getZippedAzulJdk()));
            copy.into(tempDir);
        });
        transformOutputs.dir(tempDir);
    }

    private File createTempDir() {
        try {
            return Files.createTempDirectory(
                            getZippedAzulJdk().get().getAsFile().getName())
                    .toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to make temp dir", e);
        }
    }
}
