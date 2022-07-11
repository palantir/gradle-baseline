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

import org.gradle.api.file.RegularFile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;

final class BaselineJavaCompiler implements JavaCompiler {
    private final JavaInstallationMetadata javaInstallationMetadata;

    BaselineJavaCompiler(JavaInstallationMetadata javaInstallationMetadata) {
        this.javaInstallationMetadata = javaInstallationMetadata;
    }

    @Override
    public JavaInstallationMetadata getMetadata() {
        return javaInstallationMetadata;
    }

    @Override
    public RegularFile getExecutablePath() {
        return JavaInstallationMetadataUtils.findExecutable(javaInstallationMetadata, "javac");
    }
}
