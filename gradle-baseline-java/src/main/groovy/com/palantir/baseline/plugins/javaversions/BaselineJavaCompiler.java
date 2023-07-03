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
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaCompiler;
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory;
import org.gradle.language.base.internal.compile.CompileSpec;

final class BaselineJavaCompiler extends DefaultToolchainJavaCompiler {

    private final JavaInstallationMetadata javaInstallationMetadata;
    private final JavaCompilerFactory compilerFactory;

    BaselineJavaCompiler(JavaInstallationMetadata javaInstallationMetadata, ServiceRegistry serviceRegistry) {
        super(null, null);
        this.javaInstallationMetadata = javaInstallationMetadata;
        this.compilerFactory = serviceRegistry.get(JavaCompilerFactory.class);
    }

    @Override
    public JavaInstallationMetadata getMetadata() {
        return javaInstallationMetadata;
    }

    @Override
    public RegularFile getExecutablePath() {
        return JavaInstallationMetadataUtils.findExecutable(javaInstallationMetadata, "javac");
    }

    @SuppressWarnings("unchecked")
    public <T extends CompileSpec> WorkResult execute(T spec) {
        final Class<T> specType = (Class<T>) spec.getClass();
        return compilerFactory.create(specType).execute(spec);
    }
}
