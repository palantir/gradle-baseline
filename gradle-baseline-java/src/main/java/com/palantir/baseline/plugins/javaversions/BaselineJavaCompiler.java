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

// CHECKSTYLE:OFF
import javax.inject.Inject;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.WorkResult;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaCompiler;
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory;
import org.gradle.language.base.internal.compile.CompileSpec;
// CHECKSTYLE:ON

final class BaselineJavaCompiler extends DefaultToolchainJavaCompiler {
    private static final Logger log = Logging.getLogger(BaselineJavaCompiler.class);

    private final JavaCompilerFactory compilerFactory;
    private final JavaInstallationMetadata javaInstallationMetadata;

    BaselineJavaCompiler(JavaCompilerFactory compilerFactory, JavaInstallationMetadata javaInstallationMetadata) {
        super(null, compilerFactory);
        this.compilerFactory = compilerFactory;
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

    @Override
    public <T extends CompileSpec> WorkResult execute(T spec) {
        // Copied from superclass, but avoiding using javaToolchain so we can make it null
        log.info(
                "Compiling with toolchain '{}'.",
                javaInstallationMetadata.getInstallationPath().getAsFile());
        final Class<T> specType = (Class<T>) spec.getClass();
        return compilerFactory.create(specType).execute(spec);
    }

    public static BaselineJavaCompiler create(
            ObjectFactory objectFactory, JavaInstallationMetadata javaInstallationMetadata) {
        return new BaselineJavaCompiler(
                objectFactory.newInstance(JavaCompilerFactoryGrabber.class).getJavaCompilerFactory(),
                javaInstallationMetadata);
    }

    interface JavaCompilerFactoryGrabber {
        @Inject
        JavaCompilerFactory getJavaCompilerFactory();
    }
}
