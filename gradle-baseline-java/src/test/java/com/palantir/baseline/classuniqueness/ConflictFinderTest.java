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
import com.palantir.baseline.classuniqueness.ConflictFinder.Conflict;
import com.palantir.baseline.classuniqueness.ConflictFinder.Jar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictFinderTest {
    private static final String FOO = "com/example/Foo.class";
    private static final String BAR = "com/example/Bar.class";
    private static final String FOO_CLASS = "com.example.Foo";
    private static final String BAR_CLASS = "com.example.Bar";

    @TempDir
    private Path tempDir;

    @Test
    void ignores_identical_copies() throws IOException {
        Jar first = jar("first", ImmutableMap.of(FOO, "same"));
        Jar second = jar("second", ImmutableMap.of(FOO, "same"));

        assertThat(conflictsFor(first, second))
                .as("identical class files are interchangeable, so they are not a conflict")
                .isEmpty();
    }

    @Test
    void reports_differing_copies_against_every_jar_containing_the_class() throws IOException {
        Jar first = jar(
                "first",
                ImmutableMap.<String, String>builder()
                        .put(FOO, "one")
                        .put(BAR, "bar")
                        .buildOrThrow());
        Jar second = jar("second", ImmutableMap.of(FOO, "one"));
        Jar third = jar("third", ImmutableMap.of(FOO, "two"));

        assertThat(conflictsFor(first, second, third))
                .as("a lock-file section names every jar with the class, including ones with identical copies")
                .containsExactly(new Conflict(labels("first", "second", "third"), sorted(FOO_CLASS)));
    }

    @Test
    void groups_classes_by_the_set_of_jars_containing_them() throws IOException {
        Jar first = jar(
                "first",
                ImmutableMap.<String, String>builder()
                        .put(FOO, "one")
                        .put(BAR, "one")
                        .buildOrThrow());
        Jar second = jar(
                "second",
                ImmutableMap.<String, String>builder()
                        .put(FOO, "two")
                        .put(BAR, "two")
                        .buildOrThrow());
        Jar third = jar("third", ImmutableMap.of(BAR, "three"));

        assertThat(conflictsFor(first, second, third))
                .containsOnly(
                        new Conflict(labels("first", "second"), sorted(FOO_CLASS)),
                        new Conflict(labels("first", "second", "third"), sorted(BAR_CLASS)));
    }

    @Test
    void reports_every_class_the_same_jars_conflict_on_in_one_section() throws IOException {
        Jar first = jar(
                "first",
                ImmutableMap.<String, String>builder()
                        .put(FOO, "one")
                        .put(BAR, "one")
                        .buildOrThrow());
        Jar second = jar(
                "second",
                ImmutableMap.<String, String>builder()
                        .put(FOO, "two")
                        .put(BAR, "two")
                        .buildOrThrow());

        assertThat(conflictsFor(first, second))
                .as("both classes conflict between the same two jars, so they share one lock-file section")
                .containsExactly(new Conflict(labels("first", "second"), sorted(BAR_CLASS, FOO_CLASS)));
    }

    @Test
    void ignores_differing_copies_within_one_label() throws IOException {
        // Jars are identified by their label, as the lock file shows them. Besides a malformed jar like this one, two
        // artifacts of one module can share a label, e.g. files that Gradle Module Metadata names without a
        // classifier.
        Jar malformed = jar("malformed", TestJars.writeWithDuplicateEntry(tempDir, "malformed", FOO, "one", "two"));

        assertThat(conflictsFor(malformed))
                .as("a jar with a class twice is warned about when it is indexed, not reported as a conflict")
                .isEmpty();
    }

    @Test
    void separates_distinct_classes_whose_name_hashes_collide() throws IOException {
        long collidingHash = 42;
        Jar first = jarWithEntries(
                "first",
                List.of(new ClassEntry(collidingHash, 1, FOO_CLASS), new ClassEntry(collidingHash, 2, BAR_CLASS)));
        Jar second = jarWithEntries("second", List.of(new ClassEntry(collidingHash, 3, FOO_CLASS)));

        assertThat(conflictsFor(first, second))
                .as("only Foo is in both jars; Bar merely shares its name hash")
                .containsExactly(new Conflict(labels("first", "second"), sorted(FOO_CLASS)));
    }

    @Test
    void separates_colliding_classes_with_identical_content() throws IOException {
        long collidingHash = 42;
        Jar first = jarWithEntries(
                "first",
                List.of(new ClassEntry(collidingHash, 1, FOO_CLASS), new ClassEntry(collidingHash, 2, BAR_CLASS)));
        Jar second = jarWithEntries(
                "second",
                List.of(new ClassEntry(collidingHash, 1, FOO_CLASS), new ClassEntry(collidingHash, 2, BAR_CLASS)));

        assertThat(conflictsFor(first, second))
                .as("each class is identical across the jars; their checksums only differ from each other")
                .isEmpty();
    }

    private static List<Conflict> conflictsFor(Jar... jars) throws IOException {
        return ConflictFinder.find(List.of(jars));
    }

    private static Set<JarLabel> labels(String... labels) {
        return Arrays.stream(labels).map(JarLabel::new).collect(Collectors.toSet());
    }

    private static SortedSet<String> sorted(String... names) {
        return new TreeSet<>(List.of(names));
    }

    private Jar jar(String label, Map<String, String> entries) throws IOException {
        return jar(label, TestJars.write(tempDir, label, entries));
    }

    private Jar jar(String label, Path jar) throws IOException {
        Path indexFile = Files.createTempFile(tempDir, label, ".cui");
        ClassIndex.write(jar, indexFile);
        return new Jar(new JarLabel(label), ClassIndex.read(indexFile));
    }

    private Jar jarWithEntries(String label, List<ClassEntry> entries) throws IOException {
        Path indexFile = Files.createTempFile(tempDir, label, ".cui");
        ClassIndex.writeEntries(entries, indexFile);
        return new Jar(new JarLabel(label), ClassIndex.read(indexFile));
    }
}
