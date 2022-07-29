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

import java.io.Serializable;
import java.util.Objects;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Augments {@link JavaLanguageVersion} with whether --enable-preview should be used or not. Useful for both
 * compile time and runtime.
 */
public final class ChosenJavaVersion implements Serializable {

    private final JavaLanguageVersion javaLanguageVersion;
    private final boolean enablePreview;

    public ChosenJavaVersion(JavaLanguageVersion javaLanguageVersion, boolean enablePreview) {
        this.javaLanguageVersion = javaLanguageVersion;
        this.enablePreview = enablePreview;
    }

    /** Accepts inputs like '17_PREVIEW' or '17'. */
    public static ChosenJavaVersion fromString(String string) {
        return new ChosenJavaVersion(
                JavaLanguageVersion.of(string.replaceAll("_PREVIEW", "")), string.endsWith("_PREVIEW"));
    }

    public static ChosenJavaVersion of(int number) {
        return new ChosenJavaVersion(JavaLanguageVersion.of(number), false);
    }

    public static ChosenJavaVersion of(JavaLanguageVersion version) {
        return new ChosenJavaVersion(version, false);
    }

    public JavaLanguageVersion javaLanguageVersion() {
        return javaLanguageVersion;
    }

    public boolean enablePreview() {
        return enablePreview;
    }

    public String asIdeaLanguageLevel() {
        return "JDK_" + javaLanguageVersion.toString() + (enablePreview ? "_PREVIEW" : "");
    }

    @Override
    public String toString() {
        return javaLanguageVersion.toString() + (enablePreview ? "_PREVIEW" : "");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ChosenJavaVersion that = (ChosenJavaVersion) other;
        return enablePreview == that.enablePreview && javaLanguageVersion.equals(that.javaLanguageVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaLanguageVersion, enablePreview);
    }
}
