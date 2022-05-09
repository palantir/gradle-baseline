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

import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * The purpose of this class is to provide the JavaLanguageVersion, which we know upfront, without having to resolve an
 * entire JDK, which often involves resolution, which causes Gradle to resolve the JavaCompiler/JavaLauncher etc to
 * check if toolchains are enabled. That can cause a dependency cycle, which causes a StackOverflowException.
 * This class breaks that cycle by immediately providing the JavaLanguageVersion without possibly causing a resolution.
 */
final class JavaInstallationMetadataWrapper implements JavaInstallationMetadata {
    private final JavaLanguageVersion javaLanguageVersion;
    private final Provider<JavaInstallationMetadata> delegate;

    JavaInstallationMetadataWrapper(
            JavaLanguageVersion javaLanguageVersion, Provider<JavaInstallationMetadata> delegate) {
        this.javaLanguageVersion = javaLanguageVersion;
        this.delegate = delegate;
    }

    @Override
    public JavaLanguageVersion getLanguageVersion() {
        return javaLanguageVersion;
    }

    @Override
    public String getJavaRuntimeVersion() {
        return delegate.get().getJavaRuntimeVersion();
    }

    @Override
    public String getJvmVersion() {
        return delegate.get().getJvmVersion();
    }

    @Override
    public String getVendor() {
        return delegate.get().getVendor();
    }

    @Override
    public Directory getInstallationPath() {
        return delegate.get().getInstallationPath();
    }
}
