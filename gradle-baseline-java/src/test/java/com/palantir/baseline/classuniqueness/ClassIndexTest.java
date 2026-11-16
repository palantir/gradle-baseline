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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.palantir.baseline.classuniqueness.ClassIndex.ClassEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassIndexTest {
    private static final String FOO = "com/example/Foo.class";
    private static final String FOO_CLASS = "com.example.Foo";

    @TempDir
    private Path tempDir;

    @Test
    void indexes_classes_by_dotted_name() throws IOException {
        Path jar = TestJars.write(
                tempDir,
                "jar",
                ImmutableMap.<String, String>builder()
                        .put(FOO, "foo")
                        .put("com/example/Foo$Inner.class", "inner")
                        .put("Top.class", "top")
                        .buildOrThrow());

        ClassIndex index = index(jar);

        assertThat(allNames(index)).containsExactlyInAnyOrder(FOO_CLASS, "com.example.Foo$Inner", "Top");
    }

    @Test
    void skips_directories_resources_and_excluded_classes() throws IOException {
        Path jar = TestJars.write(
                tempDir,
                "jar",
                ImmutableMap.<String, String>builder()
                        .put(FOO, "foo")
                        .put("com/example/", "")
                        .put("com/example/resource.txt", "text")
                        .put("module-info.class", "module")
                        .put("META-INF/versions/11/module-info.class", "module")
                        .put("org/apache/spark/unused/UnusedStubClass.class", "stub")
                        .buildOrThrow());

        assertThat(allNames(index(jar))).containsExactly(FOO_CLASS);
    }

    @Test
    void checksums_tell_differing_content_apart() throws IOException {
        ClassIndex one = index(TestJars.write(tempDir, "one", ImmutableMap.of(FOO, "one")));
        ClassIndex sameAsOne = index(TestJars.write(tempDir, "same", ImmutableMap.of(FOO, "one")));
        ClassIndex two = index(TestJars.write(tempDir, "two", ImmutableMap.of(FOO, "two")));

        assertThat(one.crcAndSize(0)).isEqualTo(sameAsOne.crcAndSize(0)).isNotEqualTo(two.crcAndSize(0));
    }

    @Test
    void reports_classes_the_jar_contains_more_than_once() throws IOException {
        Path jar = TestJars.writeWithDuplicateEntry(tempDir, "malformed", FOO, "one", "two");

        ClassIndex index = index(jar);
        assertThat(index.duplicatedNames()).containsExactly(FOO_CLASS);
        assertThat(allNames(index))
                .as("both entries are kept, as either could be the one that loads")
                .containsExactly(FOO_CLASS, FOO_CLASS);
    }

    @Test
    void well_formed_jar_has_no_duplicated_names() throws IOException {
        Path jar = TestJars.write(tempDir, "jar", ImmutableMap.of(FOO, "foo", "com/example/Bar.class", "bar"));

        assertThat(index(jar).duplicatedNames()).isEmpty();
    }

    @Test
    void distinct_names_with_equal_hashes_are_not_duplicated() throws IOException {
        Path indexFile = tempDir.resolve("collision.cui");
        ClassIndex.writeEntries(
                List.of(new ClassEntry(1L, 10L, FOO_CLASS), new ClassEntry(1L, 20L, "com.example.Bar")), indexFile);

        assertThat(ClassIndex.read(indexFile).duplicatedNames())
                .as("a name hash shared by two names is a collision, not a class the jar contains twice")
                .isEmpty();
    }

    private ClassIndex index(Path jar) throws IOException {
        Path indexFile = Files.createTempFile(tempDir, "index", ".cui");
        ClassIndex.write(jar, indexFile);
        return ClassIndex.read(indexFile);
    }

    private static List<String> allNames(ClassIndex index) throws IOException {
        return index.names(IntStream.range(0, index.size()).boxed().toList());
    }
}
