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
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Extension named {@code javaVersions} on the root project used to configure all java modules
 * with consistent java toolchains.
 */
@SuppressWarnings("SummaryJavadoc")
public abstract class BaselineJavaVersionsExtension implements BaselineJavaVersionsExtensionSetters {
    /** Target {@link JavaLanguageVersion} for compilation of libraries that are published. */
    public abstract Property<JavaLanguageVersion> getLibraryTarget();

    /**
     * Target {@link ChosenJavaVersion} for compilation of code used within distributions,
     * but not published externally.
     */
    public abstract Property<ChosenJavaVersion> getDistributionTarget();

    /** Runtime {@link ChosenJavaVersion} for testing and packaging distributions. */
    public abstract Property<ChosenJavaVersion> getRuntime();

    private final LazilyConfiguredMapping<JavaLanguageVersion, AtomicReference<JavaInstallationMetadata>, Project>
            jdks = new LazilyConfiguredMapping<>(AtomicReference::new);

    /**
     * Enables the setup of JDK toolchains for all subprojects.
     */
    public abstract Property<Boolean> getSetupJdkToolchains();

    @Inject
    public BaselineJavaVersionsExtension(Project project) {
        getSetupJdkToolchains().convention(true);

        // distribution defaults to the library value
        getDistributionTarget().convention(getLibraryTarget().map(ChosenJavaVersion::of));
        // runtime defaults to the distribution value
        getRuntime().convention(getDistributionTarget());

        getLibraryTarget().finalizeValueOnRead();
        getDistributionTarget().finalizeValueOnRead();
        getRuntime().finalizeValueOnRead();
    }

    /** @deprecated Use {@link #getLibraryTarget()} instead. */
    @Deprecated
    public final Property<JavaLanguageVersion> libraryTarget() {
        return getLibraryTarget();
    }

    @Override
    public final void setLibraryTarget(int value) {
        getLibraryTarget().set(JavaLanguageVersion.of(value));
    }

    @Override
    public final void setLibraryTarget(String value) {
        ChosenJavaVersion version = ChosenJavaVersion.fromString(value);
        if (version.enablePreview()) {
            throw new GradleException("Because code compiled with preview features cannot be run on newer JVMs, "
                    + "(Java 15 preview cannot be run on Java 17, e.g.) it is unsuitable for use on projects that"
                    + " are published as libraries.");
        }
        getLibraryTarget().set(version.javaLanguageVersion());
    }

    /** @deprecated Use {@link #getDistributionTarget()} instead. */
    @Deprecated
    public final Property<ChosenJavaVersion> distributionTarget() {
        return getDistributionTarget();
    }

    @Override
    public final void setDistributionTarget(int value) {
        getDistributionTarget().set(ChosenJavaVersion.of(value));
    }

    /** Accepts inputs such as '17_PREVIEW'. */
    @Override
    public final void setDistributionTarget(String value) {
        getDistributionTarget().set(ChosenJavaVersion.fromString(value));
    }

    /** @deprecated Use {@link #getRuntime()} instead. */
    @Deprecated
    public final Property<ChosenJavaVersion> runtime() {
        return getRuntime();
    }

    @Override
    public final void setRuntime(int value) {
        getRuntime().set(ChosenJavaVersion.of(value));
    }

    /** Accepts inputs such as '17_PREVIEW'. */
    @Override
    public final void setRuntime(String value) {
        getRuntime().set(ChosenJavaVersion.fromString(value));
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
