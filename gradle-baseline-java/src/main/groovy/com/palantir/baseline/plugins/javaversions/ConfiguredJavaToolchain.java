/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavadocTool;

final class ConfiguredJavaToolchain implements BaselineJavaToolchain {
    private final Provider<JavaInstallationMetadata> javaInstallationMetadata;

    ConfiguredJavaToolchain(Provider<JavaInstallationMetadata> javaInstallationMetadata) {
        this.javaInstallationMetadata = javaInstallationMetadata;
    }

    @Override
    public Provider<JavaCompiler> javaCompiler() {
        return javaInstallationMetadata.map(BaselineJavaCompiler::new);
    }

    @Override
    public Provider<JavadocTool> javadocTool() {
        return javaInstallationMetadata.map(BaselineJavadocTool::new);
    }

    @Override
    public Provider<JavaLauncher> javaLauncher() {
        return javaInstallationMetadata.map(BaselineJavaLauncher::new);
    }
}
