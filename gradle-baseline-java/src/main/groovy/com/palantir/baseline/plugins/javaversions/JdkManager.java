/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

public final class JdkManager {
    private final Path storageLocation;
    private final AzulJdkDownloader azulJdkDownloader;

    JdkManager(Path storageLocation, AzulJdkDownloader azulJdkDownloader) {
        this.storageLocation = storageLocation;
        this.azulJdkDownloader = azulJdkDownloader;
    }

    public Path jdk(JdkSpec jdkSpec) {
        Path jdkPath = storageLocation.resolve(String.format(
                "zulu-%s-%s-%s",
                jdkSpec.javaVersion(), jdkSpec.zuluVersion(), jdkSpec.hash().substring(0, 8)));

        if (Files.exists(jdkPath)) {
            return jdkPath;
        }

        Path jdkArchive = azulJdkDownloader.downloadJdkFor(jdkSpec);

        Archiver archiver = ArchiverFactory.createArchiver(jdkArchive.toFile());
        Path temporaryJdkPath = Paths.get(
                jdkPath + ".in-progress-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            try {
                archiver.extract(jdkArchive.toFile(), temporaryJdkPath.toFile());
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract jdk to directory", e);
            }

            try {
                Files.move(findJavaHome(temporaryJdkPath), jdkPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException e) {
                // This means another process has successfully installed this JVM, and we can just use their one.
            } catch (IOException e) {
                throw new RuntimeException("Failed when moving jdk from temporary location", e);
            }

            return jdkPath;
        } finally {
            try {
                FileUtils.deleteDirectory(temporaryJdkPath.toFile());
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete temporary downloaded jdk " + temporaryJdkPath, e);
            }
        }
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
