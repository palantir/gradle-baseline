/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.classuniqueness;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class TestJars {
    private TestJars() {}

    /** Writes a jar whose entries have the given paths and contents, in path order. */
    static Path write(Path dir, String name, Map<String, String> entries) throws IOException {
        Path jar = Files.createTempFile(dir, name, ".jar");
        try (OutputStream file = Files.newOutputStream(jar);
                ZipOutputStream zip = new ZipOutputStream(file)) {
            for (Map.Entry<String, String> entry : new TreeMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return jar;
    }

    /**
     * Writes a malformed jar that contains the class file at {@code path} twice, with the given contents.
     * ZipOutputStream refuses duplicate names, so this writes a second name of the same length and makes it equal in
     * the raw bytes, which renames both its local header and its central directory entry.
     */
    static Path writeWithDuplicateEntry(Path dir, String name, String path, String firstContent, String secondContent)
            throws IOException {
        int lastChar = path.length() - ".class".length() - 1;
        String otherPath =
                path.substring(0, lastChar) + (path.charAt(lastChar) == 'x' ? 'y' : 'x') + path.substring(lastChar + 1);
        Path jar = write(dir, name, Map.of(path, firstContent, otherPath, secondContent));
        String bytes = new String(Files.readAllBytes(jar), StandardCharsets.ISO_8859_1);
        Files.write(jar, bytes.replace(otherPath, path).getBytes(StandardCharsets.ISO_8859_1));
        return jar;
    }
}
