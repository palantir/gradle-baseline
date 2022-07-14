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

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Extension named {@code javaVersion} used to set the
 * target and runtime java versions used for a single project.
 */
public class BaselineJavaVersionExtension {

    private final Property<JavaLanguageVersion> target;
    private final Property<JavaLanguageVersion> runtime;

    private final Property<EnablePreview> targetEnablePreview;
    private final Property<EnablePreview> runtimeEnablePreview;
    private final Property<Boolean> overrideLibraryAutoDetection;

    @Inject
    public BaselineJavaVersionExtension(Project project) {
        target = project.getObjects().property(JavaLanguageVersion.class);
        runtime = project.getObjects().property(JavaLanguageVersion.class);
        targetEnablePreview = project.getObjects().property(EnablePreview.class);
        runtimeEnablePreview = project.getObjects().property(EnablePreview.class);
        overrideLibraryAutoDetection = project.getObjects().property(Boolean.class);

        target.finalizeValueOnRead();
        runtime.finalizeValueOnRead();
        targetEnablePreview.finalizeValueOnRead();
        runtimeEnablePreview.finalizeValueOnRead();
        overrideLibraryAutoDetection.finalizeValueOnRead();
    }

    /** Target {@link JavaLanguageVersion} for compilation. */
    public final Property<JavaLanguageVersion> target() {
        return target;
    }

    /**
     * Whether the `--enable-preview` flag should be used for compilation, producing bytecode with a minor version of
     * '65535'. Unlike normal bytecode, this bytecode cannot be run by a higher version of Java that it was compiled by.
     */
    public final Property<EnablePreview> targetEnablePreview() {
        return targetEnablePreview;
    }

    public final void setTarget(int value) {
        target.set(JavaLanguageVersion.of(value));
    }

    /** Runtime {@link JavaLanguageVersion} for testing and distributions. */
    public final Property<JavaLanguageVersion> runtime() {
        return runtime;
    }

    /**
     * Whether the `--enable-preview` flag should be passed to the java executable when running this project.
     * Must be true if {@link #targetEnablePreview} is true.
     */
    public final Property<EnablePreview> runtimeEnablePreview() {
        return runtimeEnablePreview;
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
}
