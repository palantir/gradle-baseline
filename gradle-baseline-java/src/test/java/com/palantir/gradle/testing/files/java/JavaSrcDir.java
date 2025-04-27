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

package com.palantir.gradle.testing.files.java;

import com.palantir.gradle.testing.files.GradleSourceSet;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.Language;

public record JavaSrcDir(GradleSourceSet sourceSet) {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([^;]+);");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:class|interface|record|enum)\\s+(\\w+)");

    public JavaFile writeClass(@Language("Java") String javaSource) {
        String packagePath = possiblyExtractGroup(PACKAGE_PATTERN, javaSource).orElse("");

        String className = possiblyExtractGroup(CLASS_PATTERN, javaSource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not find the class name from the source: \n\n" + javaSource));

        String canonicalClassName = packagePath + className;

        return fileByClass(canonicalClassName).overwrite(javaSource);
    }

    public JavaFile fileByClass(String canonicalClassName) {
        return fileByPath(canonicalClassName.replace('.', '/') + '/' + ".java");
    }

    public JavaFile fileByPath(String path) {
        return new JavaFile(sourceSet.path().resolve("java").resolve(path));
    }

    private static Optional<String> possiblyExtractGroup(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
