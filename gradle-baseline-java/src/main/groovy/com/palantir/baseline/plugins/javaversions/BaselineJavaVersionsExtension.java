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

import com.palantir.gradle.utils.lazilyconfiguredmapping.LazilyConfiguredMapping;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Extension named {@code javaVersions} on the root project used to configure all java modules
 * with consistent java toolchains.
 */
public class BaselineJavaVersionsExtension {
    private static final String PREVIEW_SUFFIX = "_PREVIEW";
    private final Property<JavaLanguageVersion> libraryTarget;
    private final Property<JavaLanguageVersion> distributionTarget;
    private final Property<JavaLanguageVersion> runtime;
    private final Property<Boolean> distributionEnablePreview;
    private final Property<Boolean> runtimeEnablePreview;
    private final LazilyConfiguredMapping<JavaLanguageVersion, AtomicReference<JavaInstallationMetadata>, Project>
            jdks = new LazilyConfiguredMapping<>(AtomicReference::new);

    @Inject
    public BaselineJavaVersionsExtension(Project project) {
        this.libraryTarget = project.getObjects().property(JavaLanguageVersion.class);
        this.distributionTarget = project.getObjects().property(JavaLanguageVersion.class);
        this.distributionEnablePreview = project.getObjects().property(Boolean.class);
        this.runtime = project.getObjects().property(JavaLanguageVersion.class);
        this.runtimeEnablePreview = project.getObjects().property(Boolean.class);

        // distribution defaults to the library value
        distributionTarget.convention(libraryTarget);
        // runtime defaults to the distribution value
        runtime.convention(distributionTarget);
        // distributionEnablePreview=true implicitly sets runtimeEnablePreview=true. In theory you *might* want to set
        // runtimeEnablePreview=true and distributionEnablePreview=false, but never the other way round (fail to start).
        runtimeEnablePreview.convention(distributionEnablePreview);

        libraryTarget.finalizeValueOnRead();
        distributionTarget.finalizeValueOnRead();
        runtime.finalizeValueOnRead();
        distributionEnablePreview.finalizeValueOnRead();
        runtimeEnablePreview.finalizeValueOnRead();
    }

    /** Target {@link JavaLanguageVersion} for compilation of libraries that are published. */
    public final Property<JavaLanguageVersion> libraryTarget() {
        return libraryTarget;
    }

    public final void setLibraryTarget(int value) {
        libraryTarget.set(JavaLanguageVersion.of(value));
    }

    /**
     * Target {@link JavaLanguageVersion} for compilation of code used within distributions,
     * but not published externally.
     */
    public final Property<JavaLanguageVersion> distributionTarget() {
        return distributionTarget;
    }

    public final void setDistributionTarget(int value) {
        distributionTarget.set(JavaLanguageVersion.of(value));
    }

    /** Accepts inputs such as '17_PREVIEW'. */
    public final void setDistributionTarget(String value) {
        distributionEnablePreview.set(value.contains(PREVIEW_SUFFIX));
        setDistributionTarget(Integer.parseInt(value.replaceAll(PREVIEW_SUFFIX, "")));
    }

    /** Runtime {@link JavaLanguageVersion} for testing and packaging distributions. */
    public final Property<JavaLanguageVersion> runtime() {
        return runtime;
    }

    public final void setRuntime(int value) {
        runtime.set(JavaLanguageVersion.of(value));
    }

    /** Accepts inputs such as '17_PREVIEW'. */
    public final void setRuntime(String value) {
        runtimeEnablePreview.set(value.contains(PREVIEW_SUFFIX));
        setRuntime(Integer.parseInt(value.replaceAll(PREVIEW_SUFFIX, "")));
    }

    public final Optional<JavaInstallationMetadata> jdkMetadataFor(
            JavaLanguageVersion javaLanguageVersion, Project project) {
        return jdks.get(javaLanguageVersion, project).map(AtomicReference::get);
    }

    public final void jdk(JavaLanguageVersion javaLanguageVersion, JavaInstallationMetadata javaInstallationMetadata) {
        jdks.put(javaLanguageVersion, ref -> ref.set(javaInstallationMetadata));
    }

    public final void jdks(LazyJdks lazyJdks) {
        jdks.put((javaLanguageVersion, project) -> lazyJdks.jdkFor(javaLanguageVersion, project)
                .map(javaInstallationMetadata -> ref -> ref.set(javaInstallationMetadata)));
    }

    public interface LazyJdks {
        Optional<JavaInstallationMetadata> jdkFor(JavaLanguageVersion javaLanguageVersion, Project project);
    }
}
