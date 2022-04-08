/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

final class JdkManager {
    private final Path storageLocation;
    private final AzulJdkDownloader azulJdkDownloader;

    JdkManager(Path storageLocation, AzulJdkDownloader azulJdkDownloader) {
        this.storageLocation = storageLocation;
        this.azulJdkDownloader = azulJdkDownloader;
    }

    public Path jdk(JdkSpec jdkSpec) {
        Path jdkArchive = azulJdkDownloader.downloadJdkFor(jdkSpec);
        Archiver archiver = ArchiverFactory.createArchiver(jdkArchive.toFile());
        Path temporaryJdkPath = storageLocation.resolve(jdkSpec.hash() + ".in-progress");
        try {
            archiver.extract(jdkArchive.toFile(), temporaryJdkPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract jdk to directory", e);
        }

        Path jdkPath = storageLocation.resolve(jdkSpec.hash());
        try {
            Files.move(findJavaHome(temporaryJdkPath), jdkPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to move extracted jdk to final location", e);
        }

        return jdkPath;
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
