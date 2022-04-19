/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import com.palantir.baseline.extensions.BaselineJavaVersionsExtension;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

public final class JavaToolchains {
    private final Project project;
    private final BaselineJavaVersionsExtension baselineJavaVersionsExtension;
    private final AzulJdkDownloader jdkDownloader;

    public JavaToolchains(
            Project project,
            BaselineJavaVersionsExtension baselineJavaVersionsExtension,
            AzulJdkDownloader jdkDownloader) {
        this.project = project;
        this.baselineJavaVersionsExtension = baselineJavaVersionsExtension;
        this.jdkDownloader = jdkDownloader;
    }

    public Provider<PalantirJavaToolchain> forVersion(Provider<JavaLanguageVersion> javaLanguageVersionProvider) {
        return javaLanguageVersionProvider.map(javaLanguageVersion -> {
            Provider<String> zuluVersionNumber =
                    baselineJavaVersionsExtension.zuluVersions().getting(javaLanguageVersion);
            Provider<String> javaVersionNumber =
                    baselineJavaVersionsExtension.javaVersions().getting(javaLanguageVersion);

            if (zuluVersionNumber.isPresent() ^ javaVersionNumber.isPresent()) {
                throw new IllegalArgumentException(String.format(
                        "Both the 'JAVA_X_VERSION' and 'ZULU_X_VERSION' gradle properties must be present to "
                                + "select the correct JDK."
                                + "However, only one of them was found. JAVA_X_VERSION: %s, ZULU_X_VERSION: %s",
                        zuluVersionNumber.isPresent(), javaVersionNumber.isPresent()));
            }

            if (!(zuluVersionNumber.isPresent() && javaVersionNumber.isPresent())) {
                return new FallbackGradleJavaToolchain(
                        project.getExtensions().getByType(JavaToolchainService.class), new Action<JavaToolchainSpec>() {
                            @Override
                            public void execute(JavaToolchainSpec javaToolchainSpec) {
                                javaToolchainSpec.getLanguageVersion().set(javaLanguageVersion);
                            }
                        });
            }

            return new AzulJavaToolchain(project.provider(() -> PalantirJavaInstallationMetadata.builder()
                    .languageVersion(javaLanguageVersion)
                    .jvmVersion(javaVersionNumber.get())
                    .javaRuntimeVersion(javaVersionNumber.get())
                    .vendor("Azul Zulu")
                    .installationPath(project.getLayout()
                            .dir(project.provider(() -> jdkDownloader
                                    .downloadJdkFor(JdkSpec.builder()
                                            .javaVersion(javaVersionNumber.get())
                                            .zuluVersion(zuluVersionNumber.get())
                                            .build())
                                    .toFile()))
                            .get())
                    .build()));
        });
    }
}
