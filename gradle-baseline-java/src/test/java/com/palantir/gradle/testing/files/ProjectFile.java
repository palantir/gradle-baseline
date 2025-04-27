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

package com.palantir.gradle.testing.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractPathAssert;
import org.assertj.core.api.Assertions;

public interface ProjectFile<T extends ProjectFile<T>> {
    Path path();

    default T overwrite(String text) {
        writeString(path(), text);
        return (T) this;
    }

    default T append(String text) {
        writeString(path(), text, StandardOpenOption.APPEND);
        return (T) this;
    }

    default T appendLine(String line) {
        return append(line + "\n");
    }

    default AbstractPathAssert<?> assertThat() {
        return Assertions.assertThat(path());
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
            Files.createDirectories(path.getParent());

            StandardOpenOption[] allOptions = Stream.concat(
                            Arrays.stream(options), Stream.of(StandardOpenOption.CREATE))
                    .toArray(StandardOpenOption[]::new);

            Files.writeString(path, text, StandardCharsets.UTF_8, allOptions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
