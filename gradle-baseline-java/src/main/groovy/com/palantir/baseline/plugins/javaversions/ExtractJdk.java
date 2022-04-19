/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
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
        String withoutExtension = getZippedAzulJdk().get().getAsFile().getName().replaceAll("-?\\.zip$", "");
        File outputFolder = transformOutputs.dir(withoutExtension);
        getFileSystemOperations().copy(copy -> {
            copy.from(getArchiveOperations().zipTree(getZippedAzulJdk()));
            copy.into(outputFolder);
        });
        Path innerFolder = outputFolder.toPath().resolve(withoutExtension);
        Path javaHome = findJavaHome(innerFolder);

        try (Stream<Path> topLevelJdkItems = Files.list(javaHome)) {
            topLevelJdkItems.forEach(moveTo(outputFolder.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            FileUtils.deleteDirectory(innerFolder.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Consumer<Path> moveTo(Path directory) {
        return path -> {
            Path destination = directory.resolve(path.getFileName());
            try {
                Files.move(path, destination);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Failed to move file %s to %s", path, destination, e));
            }
        };
    }

    private Path findJavaHome(Path temporaryJdkPath) {
        try (Stream<Path> files = Files.walk(temporaryJdkPath)) {
            return files.filter(file -> Files.isDirectory(file) && file.endsWith(Paths.get("Contents/Home")))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Failed to find java home in " + temporaryJdkPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to find java home in " + temporaryJdkPath, e);
        }
    }
}
