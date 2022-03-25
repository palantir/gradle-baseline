/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import java.util.Optional;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

public final class JavaToolchains {
    private final Project project;

    public JavaToolchains(Project project) {
        this.project = project;
    }

    public PalantirJavaToolchain forVersion(Provider<JavaLanguageVersion> javaLanguageVersion) {
        String jdkBaseUrl = property("base-url").orElse("https://cdn.azul.com/zulu/bin/");

        int jdkMajorVersion = javaLanguageVersion.get().asInt();

        String zuluVersionProperty = "ZULU_" + jdkMajorVersion + "_VERSION";
        String javaVersionProperty = "JAVA_" + jdkMajorVersion + "_VERSION";

        Optional<String> zuluVersionNumber = property(zuluVersionProperty);
        Optional<String> javaVersionNumber = property(javaVersionProperty);

        if (zuluVersionNumber.isPresent() ^ javaVersionNumber.isPresent()) {
            throw new IllegalArgumentException(String.format(
                    "Both the '%s' and '%s' gradle properties must be present to select the correct JDK."
                            + "However, only one of them was found. %s: %s, %s: %s",
                    zuluVersionProperty,
                    javaVersionProperty,
                    zuluVersionProperty,
                    zuluVersionNumber.isPresent(),
                    javaVersionProperty,
                    javaVersionNumber.isPresent()));
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

        String url = String.format(
                "%s/zulu%s-ca-jdk%s-%s_%s.zip",
                jdkBaseUrl, zuluVersionNumber.get(), javaVersionNumber.get(), platform(), arch());

        throw new IllegalArgumentException("Going to download " + url);
    }

    public static String platform() {
        OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isMacOsX()) {
            return "macosx";
        }
        if (operatingSystem.isLinux()) {
            return "linux";
        }
        if (operatingSystem.isWindows()) {
            return "win";
        }

        throw new UnsupportedOperationException("Cannot get platform for operation system " + operatingSystem);
    }

    public static String arch() {
        String osArch = System.getenv("os.arch");

        if (Set.of("x64", "amd64").contains(osArch)) {
            return "x64";
        }

        if (Set.of("arm", "arm64", "aarch64").contains(osArch)) {
            return "aarch64";
        }

        if (Set.of("x86", "i686").contains(osArch)) {
            return "i686";
        }

        throw new UnsupportedOperationException("Cannot get architecture for " + osArch);
    }

    private Optional<String> property(String name) {
        return Optional.ofNullable((String) project.findProperty("com.palantir.baseline-java-versions." + name));
    }
}
