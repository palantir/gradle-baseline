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

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaCompiler;
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.gradle.jvm.toolchain.internal.JavaToolchainInput;
import org.gradle.jvm.toolchain.internal.ToolchainToolFactory;
import org.gradle.util.GradleVersion;

final class BaselineJavaCompiler extends DefaultToolchainJavaCompiler {

    private final JavaInstallationMetadata javaInstallationMetadata;

    BaselineJavaCompiler(JavaInstallationMetadata javaInstallationMetadata, ServiceRegistry serviceRegistry) {
        super(toolchain(javaInstallationMetadata, serviceRegistry), serviceRegistry.get(JavaCompilerFactory.class));
        this.javaInstallationMetadata = javaInstallationMetadata;
    }

    private static JavaToolchain toolchain(JavaInstallationMetadata metadata, ServiceRegistry serviceRegistry) {
        try {
            JvmInstallationMetadata jvmInstallationMetadata = jvmInstallationMetadata(metadata);
            if (GradleVersion.current().compareTo(GradleVersion.version("7.6")) < 0) {
                return new JavaToolchain(
                        jvmInstallationMetadata,
                        serviceRegistry.get(JavaCompilerFactory.class),
                        serviceRegistry.get(ToolchainToolFactory.class),
                        serviceRegistry.get(FileFactory.class),
                        null);
            } else if (GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0) {
                Constructor<JavaToolchain> gradle76Constructor = JavaToolchain.class.getDeclaredConstructor(
                        JvmInstallationMetadata.class,
                        JavaCompilerFactory.class,
                        ToolchainToolFactory.class,
                        FileFactory.class,
                        JavaToolchainInput.class,
                        BuildOperationProgressEventEmitter.class);
                return gradle76Constructor.newInstance(
                        jvmInstallationMetadata,
                        serviceRegistry.get(JavaCompilerFactory.class),
                        serviceRegistry.get(ToolchainToolFactory.class),
                        serviceRegistry.get(FileFactory.class),
                        null,
                        null);
            } else if (GradleVersion.current().compareTo(GradleVersion.version("8.2")) < 0) {
                Constructor<JavaToolchain> gradle8Constructor = JavaToolchain.class.getDeclaredConstructor(
                        JvmInstallationMetadata.class,
                        JavaCompilerFactory.class,
                        ToolchainToolFactory.class,
                        FileFactory.class,
                        JavaToolchainInput.class,
                        boolean.class,
                        BuildOperationProgressEventEmitter.class);
                return gradle8Constructor.newInstance(
                        jvmInstallationMetadata,
                        serviceRegistry.get(JavaCompilerFactory.class),
                        serviceRegistry.get(ToolchainToolFactory.class),
                        serviceRegistry.get(FileFactory.class),
                        null,
                        false,
                        null);
            } else {
                Constructor<JavaToolchain> gradle82Constructor = JavaToolchain.class.getDeclaredConstructor(
                        JvmInstallationMetadata.class, FileFactory.class, JavaToolchainInput.class, boolean.class);
                return gradle82Constructor.newInstance(
                        jvmInstallationMetadata, serviceRegistry.get(FileFactory.class), null, false);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JvmInstallationMetadata jvmInstallationMetadata(JavaInstallationMetadata metadata) {
        return (JvmInstallationMetadata) Proxy.newProxyInstance(
                JvmInstallationMetadata.class.getClassLoader(),
                new Class[] {JvmInstallationMetadata.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getJavaHome":
                            return metadata.getInstallationPath().getAsFile().toPath();
                        case "getLanguageVersion":
                            return JavaVersion.toVersion(
                                    metadata.getLanguageVersion().asInt());
                        case "getImplementationVersion":
                        case "getJavaVersion":
                            return "1.8.0_221"; // fake version to appease runtime
                        default:
                            throw new UnsupportedOperationException(method.getName() + " not implemented here");
                    }
                });
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
