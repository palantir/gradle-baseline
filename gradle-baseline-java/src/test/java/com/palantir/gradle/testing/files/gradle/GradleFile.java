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

package com.palantir.gradle.testing.files.gradle;

import com.palantir.gradle.testing.files.ProjectFile;
import java.nio.file.Path;
import org.intellij.lang.annotations.Language;

public record GradleFile(Path path) implements ProjectFile<GradleFile> {
    @Override
    public GradleFile overwrite(@Language("Gradle") String text) {
        return ProjectFile.super.overwrite(text);
    }

    @Override
    public GradleFile append(@Language("Gradle") String text) {
        return ProjectFile.super.append(text);
    }

    @Override
    public GradleFile appendLine(@Language("Gradle") String line) {
        return ProjectFile.super.appendLine(line);
    }
}
