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
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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
 * <p>Checking a classpath only needs the name hashes and checksums. Names are read for the few classes that turn out to
 * conflict.
 *
 * <p>Layout, big-endian:
 * <pre>
 *   int      MAGIC
 *   int      n
 *   long[n]  nameHashes   murmur3_128 (first 64 bits) of the dotted class name; ascending, ties broken by crcSize
 *   long[n]  crcSizes     (CRC-32 &lt;&lt; 32) | uncompressed size, both from the zip central directory
 *   int[n+1] nameOffsets  into the names section, in the same order; the last one is the length of the names
 *   byte[]   names        UTF-8 dotted class names
 * </pre>
 */
public final class ClassIndex {
    private static final int MAGIC = 0x43554932; // "CUI2"
    private static final int HEADER_BYTES = 8;
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
        try (InputStream fileIn = Files.newInputStream(file);
                DataInputStream in = new DataInputStream(new BufferedInputStream(fileIn, BUFFER_BYTES))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not a class index: " + file);
            }
            int size = in.readInt();
            long[] nameHashes = new long[size];
            long[] crcSizes = new long[size];
            for (int i = 0; i < size; i++) {
                nameHashes[i] = in.readLong();
            }
            for (int i = 0; i < size; i++) {
                crcSizes[i] = in.readLong();
            }
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
        long sectionStart = HEADER_BYTES + 16L * size();
        int namesAt = 4 * (size() + 1);
        ByteBuffer section;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            section = ByteBuffer.allocate(Math.toIntExact(channel.size() - sectionStart));
            while (section.hasRemaining()) {
                if (channel.read(section, sectionStart + section.position()) < 0) {
                    throw new IOException("Truncated class index: " + file);
                }
            }
        }
        List<String> names = new ArrayList<>(positions.size());
        for (int position : positions) {
            int start = section.getInt(4 * position);
            int end = section.getInt(4 * position + 4);
            names.add(new String(section.array(), namesAt + start, end - start, StandardCharsets.UTF_8));
        }
        return names;
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
