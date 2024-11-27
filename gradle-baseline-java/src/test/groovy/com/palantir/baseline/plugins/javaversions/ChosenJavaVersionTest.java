/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.junit.jupiter.api.Test;

class ChosenJavaVersionTest {

    @Test
    void deserialization_produces_sensible_toString() {
        assertThat(ChosenJavaVersion.fromString("8")).hasToString("8");

        assertThat(ChosenJavaVersion.fromString("11_PREVIEW")).hasToString("11_PREVIEW");
        assertThat(ChosenJavaVersion.fromString("11_PREVIEW").enablePreview()).isTrue();
        assertThat(ChosenJavaVersion.fromString("11_PREVIEW").javaLanguageVersion())
                .isEqualTo(JavaLanguageVersion.of(11));

        assertThat(ChosenJavaVersion.fromString("17_PREVIEW")).hasToString("17_PREVIEW");
        assertThat(ChosenJavaVersion.fromString("33")).hasToString("33");

        assertThatThrownBy(() -> ChosenJavaVersion.fromString("1.5")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void idea_language_level() {
        assertThat(ChosenJavaVersion.of(11).asIdeaLanguageLevel()).isEqualTo("JDK_11");
        assertThat(ChosenJavaVersion.fromString("17_PREVIEW").asIdeaLanguageLevel())
                .isEqualTo("JDK_17_PREVIEW");
    }

    @Test
    void java_serialization() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOut = new ObjectOutputStream(baos)) {
            objectOut.writeObject(ChosenJavaVersion.fromString("11_PREVIEW"));
        }
        try (ObjectInputStream objectIn = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            Object deserialized = objectIn.readObject();
            assertThat(deserialized)
                    .describedAs("Necessary for gradle caching / up-to-dateness checking")
                    .isEqualTo(ChosenJavaVersion.fromString("11_PREVIEW"));
        }
    }
}
