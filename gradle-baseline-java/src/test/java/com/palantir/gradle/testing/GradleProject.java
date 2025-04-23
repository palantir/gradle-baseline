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

package com.palantir.gradle.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.intellij.lang.annotations.Language;

public interface GradleProject {
    Path projectDir();

    RootProject rootProject();

    default SubProject addSubproject(String name) {
        Path subprojectDir = projectDir().resolve(name);

        try {
            Files.createDirectories(subprojectDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String subprojectPath =
                rootProject().projectDir().relativize(subprojectDir).toString().replace('/', ':');

        rootProject().settingsFile().appendLine("include '%s'".formatted(subprojectPath));

        return new SubProject(name, subprojectDir, rootProject());
    }

    default GradleFile gradleFile(String name) {
        return new GradleFile(projectDir().resolve(name));
    }

    default GradleFile buildFile() {
        return gradleFile("build.gradle");
    }

    default JavaFile javaClass(@Language("Java") String javaSource) {
        String packagePath = JavaSourceUtils.extractPackage(javaSource)
                .map(pkg -> pkg.replace('.', '/') + '/')
                .orElse("");

        String className = JavaSourceUtils.extractClassName(javaSource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not find the class name from the source: \n\n" + javaSource));

        String directory = "src/main/java/" + packagePath;

        return new JavaFile(projectDir().resolve(directory + className + ".java")).overwrite(javaSource);
    }
}
