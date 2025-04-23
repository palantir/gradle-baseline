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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Stream;

public interface ProjectFile {
    Path path();

    default ProjectFile replace(String text) {
        writeString(path(), text);
        return this;
    }

    default ProjectFile append(String text) {
        writeString(path(), text, StandardOpenOption.APPEND);
        return this;
    }

    default String text() {
        try {
            return Files.readString(path());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeString(Path path, String text, StandardOpenOption... options) {
        try {
            StandardOpenOption[] allOptions = Stream.concat(
                            Arrays.stream(options), Stream.of(StandardOpenOption.CREATE))
                    .toArray(StandardOpenOption[]::new);

            Files.writeString(path, text, StandardCharsets.UTF_8, allOptions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
