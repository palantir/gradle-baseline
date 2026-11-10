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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The class names and content checksums of one jar, in a compact form that is cheap to load.
 *
 * <p>A classpath is checked on every build, but a jar's classes only change when the jar does. So each jar is indexed
 * once, and a check merges the indexes of its classpath instead of reading every class of every jar.
 *
 * <p>Finding the classes that more than one jar defines with differing content only needs a hash of each class name
 * and a checksum of its content: 16 bytes a class, which come first so a check reads just those. The names make up
 * most of the file but are only needed for the few classes that turn out to conflict, so they come last, behind a
 * table of offsets, and are read only for the jars that have conflicts.
 *
 * <p>Layout, big-endian:
 * <pre>
 *   int      MAGIC
 *   int      n            number of class entries: the length of each array below
 *   long[n]  nameHashes   murmur3_128 (first 64 bits) of the dotted class name; ascending, ties broken by crcSize
 *   long[n]  crcSizes     (CRC-32 &lt;&lt; 32) | uncompressed size, both from the zip central directory
 *   int[n+1] nameOffsets  where each name starts in names, in nameHashes order; the last one is the length of names
 *   byte[]   names        UTF-8 dotted class names, concatenated with no separator: name i is
 *                         names[nameOffsets[i], nameOffsets[i+1])
 * </pre>
 */
public final class ClassIndex {
    private static final int MAGIC = 0x43554932; // "CUI2"
    private static final int COUNT_OFFSET = Integer.BYTES;
    private static final int HEADER_BYTES = 2 * Integer.BYTES;
    /** The bytes each entry takes in nameHashes and crcSizes together. */
    private static final int ENTRY_BYTES = 2 * Long.BYTES;

    private static final int NAME_OFFSET_BYTES = Integer.BYTES;
    private static final int BUFFER_BYTES = 1 << 16;
    private static final HashFunction NAME_HASH = Hashing.murmur3_128();

    private final Path file;
    private final long[] nameHashes;
    private final long[] crcSizes;

    private ClassIndex(Path file, long[] nameHashes, long[] crcSizes) {
        this.file = file;
        this.nameHashes = nameHashes;
        this.crcSizes = crcSizes;
    }

    @VisibleForTesting
    record ClassEntry(long nameHash, long crcSize, String name) {}

    /**
     * Indexes the classes of a jar from its central directory, without decompressing any entry.
     *
     * @return the names of classes the jar contains more than once, sorted; a well-formed jar has none
     */
    public static Set<String> write(Path jar, Path indexFile) throws IOException {
        List<ClassEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String path = entry.getName();
                if (entry.isDirectory() || !path.endsWith(".class") || isExcluded(path)) {
                    continue;
                }
                String name =
                        path.substring(0, path.length() - ".class".length()).replace('/', '.');
                if (!seen.add(name)) {
                    duplicates.add(name);
                }
                long crcSize = (entry.getCrc() << 32) | (entry.getSize() & 0xFFFFFFFFL);
                entries.add(new ClassEntry(hashName(name), crcSize, name));
            }
        }
        writeEntries(entries, indexFile);
        return duplicates;
    }

    @VisibleForTesting
    static void writeEntries(List<ClassEntry> unsorted, Path indexFile) throws IOException {
        List<ClassEntry> entries = new ArrayList<>(unsorted);
        entries.sort(Comparator.comparingLong(ClassEntry::nameHash).thenComparingLong(ClassEntry::crcSize));
        try (OutputStream fileOut = Files.newOutputStream(indexFile);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fileOut, BUFFER_BYTES))) {
            out.writeInt(MAGIC);
            out.writeInt(entries.size());
            for (ClassEntry entry : entries) {
                out.writeLong(entry.nameHash());
            }
            for (ClassEntry entry : entries) {
                out.writeLong(entry.crcSize());
            }
            List<byte[]> names = entries.stream()
                    .map(entry -> entry.name().getBytes(StandardCharsets.UTF_8))
                    .toList();
            int offset = 0;
            for (byte[] name : names) {
                out.writeInt(offset);
                offset += name.length;
            }
            out.writeInt(offset);
            for (byte[] name : names) {
                out.write(name);
            }
        }
    }

    /** Loads everything but the names. */
    public static ClassIndex read(Path file) throws IOException {
        // Reads exactly the bytes needed: a check opens hundreds of mostly small indexes, so a fixed-size read buffer
        // per file would dwarf the data itself.
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer header = readFully(channel, file, 0, HEADER_BYTES);
            if (header.getInt(0) != MAGIC) {
                throw new IOException("Not a class index: " + file);
            }
            int size = header.getInt(COUNT_OFFSET);
            LongBuffer arrays = readFully(channel, file, HEADER_BYTES, Math.multiplyExact(ENTRY_BYTES, size))
                    .asLongBuffer();
            long[] nameHashes = new long[size];
            long[] crcSizes = new long[size];
            arrays.get(nameHashes).get(crcSizes);
            return new ClassIndex(file, nameHashes, crcSizes);
        }
    }

    public int size() {
        return nameHashes.length;
    }

    public long nameHash(int position) {
        return nameHashes[position];
    }

    /** Equal for identical class files; differing content gives a different value with near certainty. */
    public long crcSize(int position) {
        return crcSizes[position];
    }

    /**
     * The class names at the given positions, in the same order. Reads the whole names section at once, which is
     * cheaper than seeking to each name once more than a handful are needed.
     */
    public List<String> names(List<Integer> positions) throws IOException {
        ByteBuffer section = readNameOffsetsAndNames();
        int namesAt = NAME_OFFSET_BYTES * (size() + 1);
        List<String> names = new ArrayList<>(positions.size());
        for (int position : positions) {
            int start = section.getInt(NAME_OFFSET_BYTES * position);
            int end = section.getInt(NAME_OFFSET_BYTES * (position + 1));
            names.add(new String(
                    section.array(), section.arrayOffset() + namesAt + start, end - start, StandardCharsets.UTF_8));
        }
        return names;
    }

    /** The nameOffsets table and the names section, which run to the end of the file. */
    private ByteBuffer readNameOffsetsAndNames() throws IOException {
        long start = HEADER_BYTES + (long) ENTRY_BYTES * size();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return readFully(channel, file, start, Math.toIntExact(channel.size() - start));
        }
    }

    private static ByteBuffer readFully(FileChannel channel, Path file, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) {
                throw new IOException("Truncated class index: " + file);
            }
        }
        return buffer.flip();
    }

    private static long hashName(String dottedName) {
        return NAME_HASH.hashString(dottedName, StandardCharsets.UTF_8).asLong();
    }

    /**
     * Java 9 allows jars to have a module-info.class, we shouldn't complain about these.
     * Spark contains an 'UnusedStubClass' which generates many false positives, we shouldn't complain about this,
     * either.
     */
    private static boolean isExcluded(String path) {
        return path.endsWith("module-info.class") || path.equals("org/apache/spark/unused/UnusedStubClass.class");
    }
}
