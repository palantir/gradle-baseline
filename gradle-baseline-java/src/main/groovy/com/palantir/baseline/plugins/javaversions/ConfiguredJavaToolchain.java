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

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavadocTool;

final class ConfiguredJavaToolchain implements BaselineJavaToolchain {
    private final ObjectFactory objectFactory;
    private final Provider<JavaInstallationMetadata> javaInstallationMetadata;

    ConfiguredJavaToolchain(ObjectFactory objectFactory, Provider<JavaInstallationMetadata> javaInstallationMetadata) {
        this.objectFactory = objectFactory;
        this.javaInstallationMetadata = javaInstallationMetadata;
    }

    @Override
    public Provider<JavaCompiler> javaCompiler() {
        return javaInstallationMetadata.map(BaselineJavaCompiler::new);
    }

    @Override
    public Provider<JavadocTool> javadocTool() {
        return javaInstallationMetadata
                .map(BaselineJavadocTool::new)
                // Gradle casts to the internal type JavadocToolAdapter in the Javadoc class - so unfortunately we have
                // to use that instead of just returning the interface.
                .map(javadocTool -> BaselineJavadocToolAdapter.create(objectFactory, javadocTool));
    }

    @Override
    public Provider<JavaLauncher> javaLauncher() {
        return javaInstallationMetadata.map(BaselineJavaLauncher::new);
    }
}
