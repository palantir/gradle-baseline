/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

public final class JavaToolchains {
    private final Project project;

    public JavaToolchains(Project project) {
        this.project = project;
    }

    public PalantirJavaToolchain forVersion(Provider<JavaLanguageVersion> javaLanguageVersion) {
        return new FallbackGradleJavaToolchain(
                project.getExtensions().getByType(JavaToolchainService.class), new Action<JavaToolchainSpec>() {
                    @Override
                    public void execute(JavaToolchainSpec javaToolchainSpec) {
                        javaToolchainSpec.getLanguageVersion().set(javaLanguageVersion);
                    }
                });
    }
}
