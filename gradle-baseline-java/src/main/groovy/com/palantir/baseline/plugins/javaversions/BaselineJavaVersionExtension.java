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
@SuppressWarnings("SummaryJavadoc")
public abstract class BaselineJavaVersionExtension {
    /**
     * Target {@link ChosenJavaVersion} for compilation.
     *
     * Also determines whether the `--enable-preview` flag should be used for compilation, producing bytecode with a
     * minor version of '65535'. Unlike normal bytecode, this bytecode cannot be run by a higher version of Java that
     * it was compiled by.
     */
    public abstract Property<ChosenJavaVersion> getTarget();

    /** Runtime {@link ChosenJavaVersion} for testing and distributions. */
    public abstract Property<ChosenJavaVersion> getRuntime();

    /**
     * Overrides auto-detection if a value is present to force this module to be a
     * library ({@code true}) or a distribution {@code false}).
     */
    public abstract Property<Boolean> getOverrideLibraryAutoDetection();

    @Inject
    public BaselineJavaVersionExtension(Project project) {
        getTarget().finalizeValueOnRead();
        getRuntime().finalizeValueOnRead();
        getOverrideLibraryAutoDetection().finalizeValueOnRead();
    }

    /** @deprecated Use {@link #getTarget()} instead. */
    @Deprecated
    public final Property<ChosenJavaVersion> target() {
        return getTarget();
    }

    public final void setTarget(int value) {
        getTarget().set(ChosenJavaVersion.of(value));
    }

    public final void setTarget(String value) {
        getTarget().set(ChosenJavaVersion.fromString(value));
    }

    /** @deprecated Use {@link #getRuntime()} instead. */
    @Deprecated
    public final Property<ChosenJavaVersion> runtime() {
        return getRuntime();
    }

    public final void setRuntime(int value) {
        getRuntime().set(ChosenJavaVersion.of(value));
    }

    public final void setRuntime(String value) {
        getRuntime().set(ChosenJavaVersion.fromString(value));
    }

    /** @deprecated Use {@link #getOverrideLibraryAutoDetection()} instead. */
    @Deprecated
    public final Property<Boolean> overrideLibraryAutoDetection() {
        return getOverrideLibraryAutoDetection();
    }

    public final void library() {
        getOverrideLibraryAutoDetection().set(true);
    }
}
