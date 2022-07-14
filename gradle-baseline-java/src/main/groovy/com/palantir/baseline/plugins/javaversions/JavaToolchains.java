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

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaToolchainService;

public final class JavaToolchains {
    private final Project project;
    private final BaselineJavaVersionsExtension baselineJavaVersionsExtension;

    public JavaToolchains(Project project, BaselineJavaVersionsExtension baselineJavaVersionsExtension) {
        this.project = project;
        this.baselineJavaVersionsExtension = baselineJavaVersionsExtension;
    }

    public Provider<BaselineJavaToolchain> forVersion(Provider<ChosenJavaVersion> javaLanguageVersionProvider) {
        return javaLanguageVersionProvider.map(chosenJavaVersion -> {
            Provider<JavaInstallationMetadata> configuredJdkMetadata =
                    project.provider(() -> baselineJavaVersionsExtension
                            .jdkMetadataFor(chosenJavaVersion.javaLanguageVersion(), project)
                            .orElseGet(() -> project.getExtensions()
                                    .getByType(JavaToolchainService.class)
                                    .launcherFor(javaToolchainSpec -> javaToolchainSpec
                                            .getLanguageVersion()
                                            .set(chosenJavaVersion.javaLanguageVersion()))
                                    .get()
                                    .getMetadata()));

            return new ConfiguredJavaToolchain(
                    project.getObjects(),
                    project.provider(() -> new JavaInstallationMetadataWrapper(
                            chosenJavaVersion.javaLanguageVersion(), configuredJdkMetadata)));
        });
    }
}
