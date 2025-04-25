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

import java.nio.file.Path;
import org.intellij.lang.annotations.Language;

public final class GradleSourceSet {
    private final Path projectDir;
    private final String sourceSetName;

    public GradleSourceSet(Path projectDir, String sourceSetName) {
        this.projectDir = projectDir;
        this.sourceSetName = sourceSetName;
    }

    public JavaFile writeJavaClass(@Language("Java") String javaSource) {
        String packagePath = JavaSourceUtils.extractPackage(javaSource).orElse("");

        String className = JavaSourceUtils.extractClassName(javaSource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not find the class name from the source: \n\n" + javaSource));

        String canonicalClassName = packagePath + className;

        return javaFile(canonicalClassName).overwrite(javaSource);
    }

    public JavaFile javaFile(String canonicalClassName) {
        String directory = "src/%s/java/".formatted(sourceSetName);

        return new JavaFile(projectDir.resolve(directory + canonicalClassName.replace('.', '/') + '/' + ".java"));
    }
}
