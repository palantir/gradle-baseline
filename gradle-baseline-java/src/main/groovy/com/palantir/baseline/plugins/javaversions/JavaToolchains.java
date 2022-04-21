/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import com.palantir.baseline.extensions.BaselineJavaVersionsExtension;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

public final class JavaToolchains {
    private final Project project;
    private final BaselineJavaVersionsExtension baselineJavaVersionsExtension;

    public JavaToolchains(Project project, BaselineJavaVersionsExtension baselineJavaVersionsExtension) {
        this.project = project;
        this.baselineJavaVersionsExtension = baselineJavaVersionsExtension;
    }

    public Provider<BaselineJavaToolchain> forVersion(Provider<JavaLanguageVersion> javaLanguageVersionProvider) {
        return javaLanguageVersionProvider.flatMap(javaLanguageVersion -> {
            Provider<JavaInstallationMetadata> configuredJdk =
                    baselineJavaVersionsExtension.getJdks().getting(javaLanguageVersion);

            return configuredJdk
                    .<BaselineJavaToolchain>map(_ignored -> new ConfiguredJavaToolchain(configuredJdk))
                    .orElse(project.provider(() -> new FallbackGradleJavaToolchain(
                            project.getExtensions().getByType(JavaToolchainService.class),
                            javaToolchainSpec ->
                                    javaToolchainSpec.getLanguageVersion().set(javaLanguageVersion))));
        });
    }
}
