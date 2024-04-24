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
public class BaselineJavaVersionsExtension implements BaselineJavaVersionsExtensionSetters {
    private final Property<JavaLanguageVersion> libraryTarget;
    private final Property<ChosenJavaVersion> distributionTarget;
    private final Property<ChosenJavaVersion> runtime;
    private final LazilyConfiguredMapping<JavaLanguageVersion, AtomicReference<JavaInstallationMetadata>, Project>
            jdks = new LazilyConfiguredMapping<>(AtomicReference::new);
    private final Property<Boolean> setupJdkToolchains;

    @Inject

    public BaselineJavaVersionsExtension(Project project) {
        this.libraryTarget = project.getObjects().property(JavaLanguageVersion.class);
        this.distributionTarget = project.getObjects().property(ChosenJavaVersion.class);
        this.runtime = project.getObjects().property(ChosenJavaVersion.class);
        this.setupJdkToolchains = project.getObjects().property(Boolean.class);
        this.setupJdkToolchains.convention(true);

        // distribution defaults to the library value
        distributionTarget.convention(libraryTarget.map(ChosenJavaVersion::of));
        // runtime defaults to the distribution value
        runtime.convention(distributionTarget);

        libraryTarget.finalizeValueOnRead();
        distributionTarget.finalizeValueOnRead();
        runtime.finalizeValueOnRead();
    }

    /** Target {@link JavaLanguageVersion} for compilation of libraries that are published. */
    public final Property<JavaLanguageVersion> libraryTarget() {
        return libraryTarget;
    }

    @Override
    public final void setLibraryTarget(int value) {
        libraryTarget.set(JavaLanguageVersion.of(value));
    }

    @Override
    public final void setLibraryTarget(String value) {
        ChosenJavaVersion version = ChosenJavaVersion.fromString(value);
        if (version.enablePreview()) {
            throw new GradleException("Because code compiled with preview features cannot be run on newer JVMs, "
                    + "(Java 15 preview cannot be run on Java 17, e.g.) it is unsuitable for use on projects that"
                    + " are published as libraries.");
        }
        libraryTarget.set(version.javaLanguageVersion());
    }

    /**
     * Target {@link ChosenJavaVersion} for compilation of code used within distributions,
     * but not published externally.
     */
    public final Property<ChosenJavaVersion> distributionTarget() {
        return distributionTarget;
    }

    @Override
    public final void setDistributionTarget(int value) {
        distributionTarget.set(ChosenJavaVersion.of(value));
    }

    /** Accepts inputs such as '17_PREVIEW'. */
    @Override
    public final void setDistributionTarget(String value) {
        distributionTarget.set(ChosenJavaVersion.fromString(value));
    }

    /** Runtime {@link ChosenJavaVersion} for testing and packaging distributions. */
    public final Property<ChosenJavaVersion> runtime() {
        return runtime;
    }

    @Override
    public final void setRuntime(int value) {
        runtime.set(ChosenJavaVersion.of(value));
    }

    /** Accepts inputs such as '17_PREVIEW'. */
    @Override
    public final void setRuntime(String value) {
        runtime.set(ChosenJavaVersion.fromString(value));
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

    /**
     * Enables the setup of JDK toolchains for all subprojects.
     */
    public final Property<Boolean> getSetupJdkToolchains() {
        return setupJdkToolchains;
    }
}
