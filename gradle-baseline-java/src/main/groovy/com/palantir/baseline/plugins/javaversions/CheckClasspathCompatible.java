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

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        try (JarFile jarFile = new JarFile(file)) {
            return jarFile.stream()
                    .flatMap(entry -> {
                        // We don't care about higher versions of classes in multi-release jars as JVMs will only
                        // load classes from here that match or are higher than their current version
                        boolean isMultiReleaseClass = entry.getName().startsWith("META-INF/versions");
                        boolean isClassFile = !entry.getName().endsWith(".class");

                        if (isMultiReleaseClass || isClassFile) {
                            return Stream.empty();
                        }

                        try (InputStream inputStream = jarFile.getInputStream(entry)) {
                            return bytecodeMajorVersionForClassFile(inputStream)
                                    .filter(bytecodeMajorVersion -> bytecodeMajorVersion
                                            > getJavaVersion().get().asBytecodeMajorVersion())
                                    .map(bytecodeMajorVersion ->
                                            entry.getName() + " has bytecode major version " + bytecodeMajorVersion)
                                    .stream();
                        } catch (IOException e) {
                            throw new RuntimeException(
                                    "Failed when checking classpath compatibility of " + file + ", class "
                                            + entry.getName(),
                                    e);
                        }
                    })
                    .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed when checking classpath compatibility of: " + file, e);
        }
    }

    private static Optional<Integer> bytecodeMajorVersionForClassFile(InputStream classFile) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(classFile);
        int magic = dataInputStream.readInt();
        if (magic != BYTECODE_IDENTIFIER) {
            // Skip as it's not a class file
            return Optional.empty();
        }
        int minorBytecodeVersion = 0xFFFF & dataInputStream.readShort();
        int majorBytecodeVersion = 0xFFFF & dataInputStream.readShort();

        return Optional.of(majorBytecodeVersion);
    }
}
