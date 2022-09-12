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

/**
 * Extension named {@code javaVersion} used to set the
 * target and runtime java versions used for a single project.
 */
public class BaselineJavaVersionExtension {

    private final Property<ChosenJavaVersion> target;
    private final Property<ChosenJavaVersion> runtime;

    private final Property<Boolean> overrideLibraryAutoDetection;

    @Inject
    public BaselineJavaVersionExtension(Project project) {
        target = project.getObjects().property(ChosenJavaVersion.class);
        runtime = project.getObjects().property(ChosenJavaVersion.class);
        overrideLibraryAutoDetection = project.getObjects().property(Boolean.class);

        target.finalizeValueOnRead();
        runtime.finalizeValueOnRead();
        overrideLibraryAutoDetection.finalizeValueOnRead();
    }

    /**
     * Target {@link ChosenJavaVersion} for compilation.
     *
     * Also determines whether the `--enable-preview` flag should be used for compilation, producing bytecode with a
     * minor version of '65535'. Unlike normal bytecode, this bytecode cannot be run by a higher version of Java that
     * it was compiled by.
     */
    public final Property<ChosenJavaVersion> target() {
        return target;
    }

    public final void setTarget(int value) {
        target.set(ChosenJavaVersion.of(value));
    }

    /** Runtime {@link ChosenJavaVersion} for testing and distributions. */
    public final Property<ChosenJavaVersion> runtime() {
        return runtime;
    }

    public final void setRuntime(int value) {
        runtime.set(ChosenJavaVersion.of(value));
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
