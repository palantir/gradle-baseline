/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.extensions;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Extension named {@code javaVersion} used to set the
 * target and runtime java versions used for a single project.
 */
public class BaselineJavaVersionExtension {
    private static final Pattern ZULU_VERSION_PATTERN = Pattern.compile("ZULU_([\\d]+)_VERSION");
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("JAVA_([\\d]+)_VERSION");

    private final Property<JavaLanguageVersion> target;
    private final Property<JavaLanguageVersion> runtime;
    private final Property<Boolean> overrideLibraryAutoDetection;
    private final MapProperty<JavaLanguageVersion, String> zuluVersions;
    private final MapProperty<JavaLanguageVersion, String> javaVersions;

    @Inject
    public BaselineJavaVersionExtension(Project project) {
        target = project.getObjects().property(JavaLanguageVersion.class);
        target.finalizeValueOnRead();

        runtime = project.getObjects().property(JavaLanguageVersion.class);
        runtime.finalizeValueOnRead();

        overrideLibraryAutoDetection = project.getObjects().property(Boolean.class);
        overrideLibraryAutoDetection.finalizeValueOnRead();

        zuluVersions = project.getObjects().mapProperty(JavaLanguageVersion.class, String.class);
        zuluVersions.putAll(project.provider(() -> parseOutVersions(project, ZULU_VERSION_PATTERN)));
        zuluVersions.finalizeValueOnRead();

        javaVersions = project.getObjects().mapProperty(JavaLanguageVersion.class, String.class);
        javaVersions.putAll(project.provider(() -> parseOutVersions(project, JAVA_VERSION_PATTERN)));
        javaVersions.finalizeValueOnRead();
    }

    /** Target {@link JavaLanguageVersion} for compilation. */
    public final Property<JavaLanguageVersion> target() {
        return target;
    }

    public final void setTarget(int value) {
        target.set(JavaLanguageVersion.of(value));
    }

    /** Runtime {@link JavaLanguageVersion} for testing and distributions. */
    public final Property<JavaLanguageVersion> runtime() {
        return runtime;
    }

    public final void setRuntime(int value) {
        runtime.set(JavaLanguageVersion.of(value));
    }

    /**
     * Overrides auto-detection if a value is present to force this module to be a
     * library ({@code true}) or a distribution {@code false}).
     */
    public final Property<Boolean> overrideLibraryAutoDetection() {
        return overrideLibraryAutoDetection;
    }

    public final void library() {
        overrideLibraryAutoDetection.set(true);
    }

    public final MapProperty<JavaLanguageVersion, String> zuluVersions() {
        return zuluVersions;
    }

    public final MapProperty<JavaLanguageVersion, String> javaVersions() {
        return javaVersions;
    }

    private static Map<JavaLanguageVersion, String> parseOutVersions(Project project, Pattern propertyPattern) {
        Map<JavaLanguageVersion, String> ret = new HashMap<>();

        project.getProperties().forEach((key, value) -> {
            Matcher matcher = propertyPattern.matcher(key);

            if (!matcher.matches()) {
                return;
            }

            ret.put(JavaLanguageVersion.of(Integer.parseInt(matcher.group(1))), (String) value);
        });

        return ret;
    }
}
