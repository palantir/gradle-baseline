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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Finds classes that more than one jar on a classpath defines with differing content. */
public final class ConflictFinder {
    private ConflictFinder() {}

    /** A jar on the classpath: how it is shown in the lock file, and its index. */
    public record Jar(JarLabel label, ClassIndex index) {}

    /**
     * Classes that all the given jars contain, with differing content in at least two of them: one section of the lock
     * file.
     */
    public record Conflict(Set<JarLabel> jarLabels, SortedSet<String> classNames) {}

    /** One class in a jar's index, without its name: its position in that index, and its content checksum. */
    private record IndexEntry(Jar jar, int position, long crcAndSize) {}

    /** An index entry once its class name has been read. */
    private record NamedEntry(IndexEntry entry, String className) {}

    public static List<Conflict> find(List<Jar> classpath) throws IOException {
        return conflicts(readNames(candidates(classpath)));
    }

    /**
     * Groups of entries that share a name hash but not their content: the only entries whose names are needed.
     * Identical copies are harmless and by far the most common kind of duplicate, so they are dropped here.
     */
    private static List<List<IndexEntry>> candidates(List<Jar> classpath) {
        long[] duplicatedNameHashes = duplicatedNameHashes(classpath);
        return entriesWithNameHashIn(classpath, duplicatedNameHashes).stream()
                .filter(ConflictFinder::contentDiffers)
                .toList();
    }

    /**
     * Name hashes found in more than one entry across the classpath, ascending. Almost every class is in exactly one
     * jar, so this is a tiny fraction of the classpath. The hashes stay primitive {@code long}s: this sorts one for
     * every class on the classpath.
     */
    private static long[] duplicatedNameHashes(List<Jar> classpath) {
        long[] allNameHashes =
                new long[classpath.stream().mapToInt(jar -> jar.index().size()).sum()];
        int next = 0;
        for (Jar jar : classpath) {
            for (int position = 0; position < jar.index().size(); position++) {
                allNameHashes[next] = jar.index().nameHash(position);
                next++;
            }
        }
        Arrays.sort(allNameHashes);

        // After sorting, equal hashes sit next to each other. Keep one copy of every run longer than one, moving it
        // to the front of the array, which the loop has already read past.
        int duplicated = 0;
        int start = 0;
        while (start < allNameHashes.length) {
            int end = start + 1;
            while (end < allNameHashes.length && allNameHashes[end] == allNameHashes[start]) {
                end++;
            }
            if (end - start > 1) {
                allNameHashes[duplicated] = allNameHashes[start];
                duplicated++;
            }
            start = end;
        }
        return Arrays.copyOf(allNameHashes, duplicated);
    }

    /** Every entry whose name hash is in {@code duplicatedNameHashes}, grouped by that hash. */
    private static Collection<List<IndexEntry>> entriesWithNameHashIn(
            List<Jar> classpath, long[] duplicatedNameHashes) {
        Map<Long, List<IndexEntry>> entriesByNameHash = new HashMap<>();
        for (Jar jar : classpath) {
            ClassIndex index = jar.index();
            for (int position = 0; position < index.size(); position++) {
                long nameHash = index.nameHash(position);
                if (Arrays.binarySearch(duplicatedNameHashes, nameHash) >= 0) {
                    entriesByNameHash
                            .computeIfAbsent(nameHash, _nameHash -> new ArrayList<>())
                            .add(new IndexEntry(jar, position, index.crcAndSize(position)));
                }
            }
        }
        return entriesByNameHash.values();
    }

    /** Reads names one jar at a time, so each index file is read at most once. */
    private static List<NamedEntry> readNames(List<List<IndexEntry>> candidates) throws IOException {
        Map<Jar, List<IndexEntry>> entriesByJar = new HashMap<>();
        for (List<IndexEntry> sameNameHash : candidates) {
            for (IndexEntry entry : sameNameHash) {
                entriesByJar
                        .computeIfAbsent(entry.jar(), _jar -> new ArrayList<>())
                        .add(entry);
            }
        }
        List<NamedEntry> namedEntries = new ArrayList<>();
        for (Map.Entry<Jar, List<IndexEntry>> sameJar : entriesByJar.entrySet()) {
            Jar jar = sameJar.getKey();
            List<IndexEntry> jarEntries = sameJar.getValue();
            List<String> names = jar.index()
                    .names(jarEntries.stream().map(IndexEntry::position).toList());
            for (int i = 0; i < jarEntries.size(); i++) {
                namedEntries.add(new NamedEntry(jarEntries.get(i), names.get(i)));
            }
        }
        return namedEntries;
    }

    /**
     * Classes found in more than one jar with differing content, grouped by the jars containing them. Entries are
     * grouped by their name rather than their name hash, as distinct names can, rarely, have equal hashes.
     */
    private static List<Conflict> conflicts(List<NamedEntry> namedEntries) {
        Map<String, List<IndexEntry>> entriesByClassName = new HashMap<>();
        for (NamedEntry named : namedEntries) {
            entriesByClassName
                    .computeIfAbsent(named.className(), _className -> new ArrayList<>())
                    .add(named.entry());
        }
        Map<Set<JarLabel>, SortedSet<String>> classNamesByJarLabels = new HashMap<>();
        for (Map.Entry<String, List<IndexEntry>> sameName : entriesByClassName.entrySet()) {
            String className = sameName.getKey();
            List<IndexEntry> entries = sameName.getValue();
            Set<JarLabel> jarLabels = new HashSet<>();
            for (IndexEntry entry : entries) {
                jarLabels.add(entry.jar().label());
            }
            if (jarLabels.size() > 1 && contentDiffers(entries)) {
                classNamesByJarLabels
                        .computeIfAbsent(jarLabels, _jarLabels -> new TreeSet<>())
                        .add(className);
            }
        }
        List<Conflict> conflicts = new ArrayList<>(classNamesByJarLabels.size());
        for (Map.Entry<Set<JarLabel>, SortedSet<String>> sameJars : classNamesByJarLabels.entrySet()) {
            Set<JarLabel> jarLabels = sameJars.getKey();
            SortedSet<String> classNames = sameJars.getValue();
            conflicts.add(new Conflict(jarLabels, classNames));
        }
        return conflicts;
    }

    private static boolean contentDiffers(List<IndexEntry> entries) {
        long first = entries.get(0).crcAndSize();
        for (IndexEntry entry : entries) {
            if (entry.crcAndSize() != first) {
                return true;
            }
        }
        return false;
    }
}
