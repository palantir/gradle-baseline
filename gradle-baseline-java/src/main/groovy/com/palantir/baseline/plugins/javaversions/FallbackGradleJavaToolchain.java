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

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JavadocTool;

public final class FallbackGradleJavaToolchain implements BaselineJavaToolchain {
    private final JavaToolchainService javaToolchainService;
    private final Action<JavaToolchainSpec> configureJavaToolchainSpec;

    public FallbackGradleJavaToolchain(
            JavaToolchainService javaToolchainService, Action<JavaToolchainSpec> configureJavaToolchainSpec) {
        this.javaToolchainService = javaToolchainService;
        this.configureJavaToolchainSpec = configureJavaToolchainSpec;
    }

    @Override
    public Provider<JavaCompiler> javaCompiler() {
        return javaToolchainService.compilerFor(configureJavaToolchainSpec);
    }

    @Override
    public Provider<JavadocTool> javadocTool() {
        return javaToolchainService.javadocToolFor(configureJavaToolchainSpec);
    }

    @Override
    public Provider<JavaLauncher> javaLauncher() {
        return javaToolchainService.launcherFor(configureJavaToolchainSpec);
    }
}
