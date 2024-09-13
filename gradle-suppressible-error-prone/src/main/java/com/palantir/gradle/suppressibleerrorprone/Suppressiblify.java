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
import java.util.function.Function;
import java.util.function.Predicate;
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
        String inputName = getInputArtifact().get().getAsFile().getName();

        if (inputName.startsWith("error_prone_check_api")) {
            if (getParameters().getSuppressionStage1().get()) {
                suppressCheckApi(outputs.file("error_prone_check_api_suppressible_error_prone_modified.jar"));
                return;
            }
        }

        outputs.file(getInputArtifact());
    }

    private void suppressCheckApi(File output) {

        Function<InputStream, byte[]> classTransformer = inputStream -> {
            try {
                ClassReader classReader = new ClassReader(inputStream);
                ClassWriter classWriter = new ClassWriter(classReader, 0);
                VisitorStateClassVisitor visitorStateClassVisitor =
                        new VisitorStateClassVisitor(Opcodes.ASM9, classWriter);
                classReader.accept(visitorStateClassVisitor, 0);
                return classWriter.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        visitJar(output, className -> className.equals("com/google/errorprone/VisitorState.class"), classTransformer);
    }

    private void visitJar(
            File output, Predicate<String> shouldChangeClass, Function<InputStream, byte[]> classTransformer) {
        try (ZipOutputStream zipOutputStream =
                        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)));
                JarFile jarFile = new JarFile(getInputArtifact().get().getAsFile())) {
            jarFile.stream().forEach(jarEntry -> {
                try {
                    if (jarEntry.getName().endsWith(".class") && shouldChangeClass.test(jarEntry.getName())) {

                        byte[] newClassBytes = classTransformer.apply(jarFile.getInputStream(jarEntry));

                        jarEntry.setSize(newClassBytes.length);
                        jarEntry.setCompressedSize(-1);
                        zipOutputStream.putNextEntry(jarEntry);
                        zipOutputStream.write(newClassBytes);
                        zipOutputStream.closeEntry();
                    } else {
                        zipOutputStream.putNextEntry(jarEntry);
                        jarFile.getInputStream(jarEntry).transferTo(zipOutputStream);
                        zipOutputStream.closeEntry();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public enum SuppressionStage {
        STAGE1,
        STAGE2
    }

    public abstract static class SParams implements TransformParameters {
        @Input
        public abstract Property<String> getCacheBust();

        @Input
        public abstract Property<Boolean> getSuppressionStage1();
    }
}
