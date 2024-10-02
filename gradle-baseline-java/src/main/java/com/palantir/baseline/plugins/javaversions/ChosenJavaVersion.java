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

import com.google.common.base.Strings;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Augments {@link JavaLanguageVersion} with whether --enable-preview should be used or not. Useful for both
 * compile time and runtime.
 */
public final class ChosenJavaVersion implements Serializable {

    private static final Pattern PREVIEW_PATTERN = Pattern.compile("^(\\d+)_PREVIEW$");
    private static final Pattern EXPERIMENTAL_PATTERN = Pattern.compile("^(\\d+)-(.*)$");

    private final JavaLanguageVersion javaLanguageVersion;
    private final boolean enablePreview;
    private final String experimentalSuffix;

    public ChosenJavaVersion(JavaLanguageVersion javaLanguageVersion, boolean enablePreview) {
        this.javaLanguageVersion = javaLanguageVersion;
        this.enablePreview = enablePreview;
        this.experimentalSuffix = "";
    }

    public ChosenJavaVersion(JavaLanguageVersion javaLanguageVersion, String experimentalSuffix) {
        this.javaLanguageVersion = javaLanguageVersion;
        this.enablePreview = true;
        this.experimentalSuffix = experimentalSuffix;
    }

    /** Accepts inputs like '17_PREVIEW' or '17', or '24-loom-experimental'. */
    public static ChosenJavaVersion fromString(String string) {
        try {
            return new ChosenJavaVersion(JavaLanguageVersion.of(string), false);
        } catch (NumberFormatException e) {
            // not a JDK release version, check for preview or experimental suffix
            Matcher previewMatcher = PREVIEW_PATTERN.matcher(string);
            Matcher experimentalMatcher = EXPERIMENTAL_PATTERN.matcher(string);
            if (previewMatcher.matches()) {
                return new ChosenJavaVersion(JavaLanguageVersion.of(previewMatcher.group(1)), true);
            } else if (experimentalMatcher.matches()) {
                String experimentalSuffix = "-" + experimentalMatcher.group(2);
                return new ChosenJavaVersion(JavaLanguageVersion.of(experimentalMatcher.group(1)), experimentalSuffix);
            } else {
                throw new IllegalArgumentException(
                        "wrong format for ChosenJavaVersion, must be one of: `<majorVersion>`;"
                                + " `<majorVersion>_PREVIEW`; or `<majorVersion>-<experimentalSuffix>`");
            }
        }
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

    public Optional<String> experimentalSuffix() {
        return Strings.isNullOrEmpty(experimentalSuffix) ? Optional.empty() : Optional.of(experimentalSuffix);
    }

    public String asIdeaLanguageLevel() {
        if (!Strings.isNullOrEmpty(experimentalSuffix)) {
            return "JDK_X";
        } else {
            return "JDK_" + javaLanguageVersion.toString() + (enablePreview ? "_PREVIEW" : "");
        }
    }

    public int asBytecodeMajorVersion() {
        // 52 was the major version for Java 8, after that each java major version increments the bytecode major
        // version by 1. This doesn't work for Java 1.4 and below.
        return javaLanguageVersion.asInt() + (52 - 8);
    }

    @Override
    public String toString() {
        if (!Strings.isNullOrEmpty(experimentalSuffix)) {
            return javaLanguageVersion.toString() + experimentalSuffix;
        } else {
            return javaLanguageVersion.toString() + (enablePreview ? "_PREVIEW" : "");
        }
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
        return enablePreview == that.enablePreview
                && Objects.equals(javaLanguageVersion, that.javaLanguageVersion)
                && Objects.equals(experimentalSuffix, that.experimentalSuffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaLanguageVersion, enablePreview, experimentalSuffix);
    }
}
