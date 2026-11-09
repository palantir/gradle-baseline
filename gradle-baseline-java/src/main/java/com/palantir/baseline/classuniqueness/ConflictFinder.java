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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.LongStream;

/** Finds classes that more than one jar on a classpath defines with differing content. */
public final class ConflictFinder {
    private ConflictFinder() {}

    /** A jar on the classpath: how it is shown in the lock file, and its index. */
    public record Jar(String label, ClassIndex index) {}

    /** One class entry: which jar on the classpath, its position in that jar's index, and its content checksum. */
    private record Entry(int jar, int position, long crcSize) {}

    /**
     * Class names that jars define with differing content, keyed by the labels of every jar containing them: the
     * shape of a lock-file section.
     */
    public static Map<Set<String>, SortedSet<String>> find(List<Jar> classpath) throws IOException {
        List<List<Entry>> candidates = candidates(classpath);
        Map<Entry, String> names = readNames(classpath, candidates);
        return differingClasses(classpath, candidates, names);
    }

    /**
     * Groups of entries that share a name hash but not their content: the only entries whose names are needed.
     * Identical copies are harmless and by far the most common kind of duplicate, so they are dropped here.
     */
    private static List<List<Entry>> candidates(List<Jar> classpath) {
        long[] duplicated = duplicatedNameHashes(classpath);
        return entriesWithNameHashIn(classpath, duplicated).values().stream()
                .filter(ConflictFinder::contentDiffers)
                .toList();
    }

    /**
     * Name hashes found in more than one entry across the classpath, ascending. Almost every class is in exactly one
     * jar, so this is a tiny fraction of the classpath.
     */
    private static long[] duplicatedNameHashes(List<Jar> classpath) {
        long[] all =
                new long[classpath.stream().mapToInt(jar -> jar.index().size()).sum()];
        int at = 0;
        for (Jar jar : classpath) {
            for (int position = 0; position < jar.index().size(); position++) {
                all[at++] = jar.index().nameHash(position);
            }
        }
        Arrays.sort(all);

        // After sorting, equal hashes sit next to each other. Keep one copy of every run longer than one.
        LongStream.Builder duplicated = LongStream.builder();
        int start = 0;
        while (start < all.length) {
            int end = start + 1;
            while (end < all.length && all[end] == all[start]) {
                end++;
            }
            if (end - start > 1) {
                duplicated.add(all[start]);
            }
            start = end;
        }
        return duplicated.build().toArray();
    }

    /** Every entry whose name hash is in {@code duplicated}, grouped by that hash. */
    private static Map<Long, List<Entry>> entriesWithNameHashIn(List<Jar> classpath, long[] duplicated) {
        Map<Long, List<Entry>> entriesByHash = new HashMap<>();
        for (int jar = 0; jar < classpath.size(); jar++) {
            ClassIndex index = classpath.get(jar).index();
            for (int position = 0; position < index.size(); position++) {
                long hash = index.nameHash(position);
                if (Arrays.binarySearch(duplicated, hash) >= 0) {
                    entriesByHash
                            .computeIfAbsent(hash, _hash -> new ArrayList<>())
                            .add(new Entry(jar, position, index.crcSize(position)));
                }
            }
        }
        return entriesByHash;
    }

    /** Reads names one jar at a time, so each index file is read at most once. */
    private static Map<Entry, String> readNames(List<Jar> classpath, List<List<Entry>> candidates) throws IOException {
        Map<Integer, List<Entry>> byJar = new HashMap<>();
        for (List<Entry> entries : candidates) {
            for (Entry entry : entries) {
                byJar.computeIfAbsent(entry.jar(), _jar -> new ArrayList<>()).add(entry);
            }
        }
        Map<Entry, String> names = new HashMap<>();
        for (Map.Entry<Integer, List<Entry>> sameJar : byJar.entrySet()) {
            List<Entry> entries = sameJar.getValue();
            entries.sort(Comparator.comparingInt(Entry::position));
            List<String> jarNames = classpath
                    .get(sameJar.getKey())
                    .index()
                    .names(entries.stream().map(Entry::position).toList());
            for (int i = 0; i < entries.size(); i++) {
                names.put(entries.get(i), jarNames.get(i));
            }
        }
        return names;
    }

    private static Map<Set<String>, SortedSet<String>> differingClasses(
            List<Jar> classpath, List<List<Entry>> candidates, Map<Entry, String> names) {
        Map<Set<String>, SortedSet<String>> classesByJars = new HashMap<>();
        for (List<Entry> entries : candidates) {
            // Grouping by name also separates distinct classes whose 64-bit name hashes happen to collide.
            Map<String, List<Entry>> byName = new HashMap<>();
            for (Entry entry : entries) {
                byName.computeIfAbsent(names.get(entry), _name -> new ArrayList<>())
                        .add(entry);
            }
            byName.forEach((name, sameName) -> {
                Set<String> jars = new HashSet<>();
                for (Entry entry : sameName) {
                    jars.add(classpath.get(entry.jar()).label());
                }
                if (jars.size() > 1 && contentDiffers(sameName)) {
                    classesByJars
                            .computeIfAbsent(jars, _jars -> new TreeSet<>())
                            .add(name);
                }
            });
        }
        return classesByJars;
    }

    private static boolean contentDiffers(List<Entry> entries) {
        long first = entries.get(0).crcSize();
        for (Entry entry : entries) {
            if (entry.crcSize() != first) {
                return true;
            }
        }
        return false;
    }
}
