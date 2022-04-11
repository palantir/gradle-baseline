/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.gradle.internal.os.OperatingSystem;
import org.immutables.value.Value;

@Value.Immutable
interface JdkSpec {
    String javaVersion();

    String zuluVersion();

    @Value.Default
    default String os() {
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

    @Value.Default
    default String arch() {
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

    default String hash() {
        return Hashing.sha256()
                .hashString(
                        String.format("azul-jdk:%s:%s:%s:%s", javaVersion(), zuluVersion(), os(), arch()),
                        StandardCharsets.UTF_8)
                .toString();
    }

    class Builder extends ImmutableJdkSpec.Builder {}

    static Builder builder() {
        return new Builder();
    }
}
