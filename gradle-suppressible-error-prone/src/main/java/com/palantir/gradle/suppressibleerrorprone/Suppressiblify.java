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

package com.palantir.gradle.suppressibleerrorprone;

import com.palantir.gradle.suppressibleerrorprone.Suppressiblify.SParams;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public abstract class Suppressiblify implements TransformAction<SParams> {
    @InputArtifact
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public final void transform(TransformOutputs outputs) {
        File output = outputs.file(getInputArtifact().get().getAsFile().getName());

        ClassFileVisitor hasBugChecker = (jarEntry, classReader) -> {
            return !SuppressifyingClassVisitor.BUG_CHECKER.equals(classReader.getSuperName());
        };

        if (visitClassFiles(hasBugChecker)) {
            try {
                Files.copy(getInputArtifact().get().getAsFile().toPath(), output.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        try (ZipOutputStream zipOutputStream =
                new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            visitClassFiles(new ClassFileVisitor() {
                @Override
                public boolean continueAfterReading(JarEntry jarEntry, ClassReader classReader) {
                    ClassWriter classWriter = new ClassWriter(classReader, 0);
                    SuppressifyingClassVisitor suppressifyingClassVisitor =
                            new SuppressifyingClassVisitor(Opcodes.ASM9, classWriter);
                    classReader.accept(suppressifyingClassVisitor, 0);
                    byte[] newClassBytes = classWriter.toByteArray();

                    jarEntry.setSize(newClassBytes.length);
                    jarEntry.setCompressedSize(-1);
                    try {
                        zipOutputStream.putNextEntry(jarEntry);
                        zipOutputStream.write(newClassBytes);
                        zipOutputStream.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                }

                @Override
                public void visitNonClassFile(JarEntry jarEntry, InputStream inputStream) {
                    try {
                        zipOutputStream.putNextEntry(jarEntry);
                        inputStream.transferTo(zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    interface ClassFileVisitor {
        default void visitNonClassFile(JarEntry jarEntry, InputStream inputStream) {}

        boolean continueAfterReading(JarEntry jarEntry, ClassReader classReader);
    }

    private boolean visitClassFiles(ClassFileVisitor classFileVisitor) {
        try (JarFile jarFile = new JarFile(getInputArtifact().get().getAsFile())) {
            long totalEntriesVisited = jarFile.stream()
                    .takeWhile(jarEntry -> {
                        try {
                            if (!jarEntry.getName().endsWith(".class")) {
                                classFileVisitor.visitNonClassFile(jarEntry, jarFile.getInputStream(jarEntry));
                                return true;
                            }

                            ClassReader classReader = new ClassReader(jarFile.getInputStream(jarEntry));
                            return classFileVisitor.continueAfterReading(jarEntry, classReader);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .count();

            return totalEntriesVisited == jarFile.size();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract static class SParams implements TransformParameters {
        @Input
        public abstract Property<String> getCacheBust();
    }
}
