/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins.javaversions;

import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import org.apache.commons.io.input.BoundedInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class CheckClasspathCompatible extends DefaultTask {
    private static final int BYTECODE_IDENTIFIER = 0xCAFEBABE;

    @Console
    public abstract Property<String> getClasspathName();

    @Input
    public abstract Property<ChosenJavaVersion> getJavaVersion();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @TaskAction
    public final void action() {
        String exampleBadClassesPerJar = getClasspath().getFiles().stream()
                .filter(file -> file.getName().endsWith(".jar"))
                .flatMap(file ->
                        tooHighBytecodeMajorVersionInJar(file)
                                .map(exampleClassInJar -> file.getAbsolutePath() + ": " + exampleClassInJar)
                                .stream())
                .collect(Collectors.joining("\n"));

        if (exampleBadClassesPerJar.isEmpty()) {
            return;
        }

        throw new RuntimeException(String.format(
                "The %s classpath has the following jars which contain classes that "
                        + "have too high a java language version. "
                        + "We're expecting the bytecode major version to be no "
                        + "more than %d for java language version %d. "
                        + "Examples classes in each jar:\n\n%s",
                getClasspathName().get(),
                getJavaVersion().get().asBytecodeMajorVersion(),
                getJavaVersion().get().javaLanguageVersion().asInt(),
                exampleBadClassesPerJar));
    }

    private Optional<String> tooHighBytecodeMajorVersionInJar(File file) {
        try (JarInputStream jarInputStream = new JarInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            JarEntry entry;
            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                // We don't care about higher versions of classes in multi-release jars as JVMs will only
                // load classes from here that match or are higher than their current version
                boolean isMultiReleaseClass = entryName.contains("META-INF/versions");
                boolean isntClassFile = !entryName.endsWith(".class");

                if (isMultiReleaseClass || isntClassFile) {
                    continue;
                }

                InputStream classInputStream = new BoundedInputStream(jarInputStream, entry.getSize());
                Optional<String> bytecodeMajorVersionTooHigh = bytecodeMajorVersionForClassFile(classInputStream)
                        .filter(bytecodeMajorVersion ->
                                bytecodeMajorVersion > getJavaVersion().get().asBytecodeMajorVersion())
                        .map(bytecodeMajorVersion -> entryName + " has bytecode major version " + bytecodeMajorVersion);

                if (bytecodeMajorVersionTooHigh.isPresent()) {
                    return bytecodeMajorVersionTooHigh;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed when checking classpath compatibility of: " + file, e);
        }

        return Optional.empty();
    }

    private static Optional<Integer> bytecodeMajorVersionForClassFile(InputStream classFile) throws IOException {
        // Avoid DataInputStream as it allocates 240+ bytes on construction
        byte[] buf = new byte[4];
        ByteStreams.readFully(classFile, buf);
        int magic = Ints.fromByteArray(buf);

        if (magic != BYTECODE_IDENTIFIER) {
            // Skip as it's not a class file
            return Optional.empty();
        }

        // Read the minor and major version (both u16s)
        ByteStreams.readFully(classFile, buf);

        // The first two bytes make up the minor version, so take the second
        int majorBytecodeVersion = 0xFFFF & Shorts.fromBytes(buf[2], buf[3]);

        return Optional.of(majorBytecodeVersion);
    }
}
