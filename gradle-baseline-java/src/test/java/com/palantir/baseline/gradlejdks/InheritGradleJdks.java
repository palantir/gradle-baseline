/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.gradlejdks;

import com.palantir.gradle.testing.files.Directory;
import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InheritGradleJdks {

    // Ideally this would be a Junit 5 extension (BeforeEachCallback) - but no way to get the RootProjet from the
    // gradle-plugin-testing ParameterResolver :(
    public static void beforeEach(RootProject rootProject) {
        rootProject.gradlePropertiesFile().appendProperty("palantir.jdk.setup.enabled", "true");
        rootProject.settingsGradle().plugins().add("com.palantir.jdks.settings");
        rootProject.buildGradle().plugins().add("com.palantir.jdks");
        Directory jdksDir = rootProject.directory("gradle").createDirectories().directory("jdks");

        try {
            Files.createSymbolicLink(jdksDir.path(), Path.of("../gradle/jdks").toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InheritGradleJdks() {}
}
