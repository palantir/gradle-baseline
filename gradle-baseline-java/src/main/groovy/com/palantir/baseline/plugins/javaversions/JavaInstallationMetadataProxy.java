/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * The purpose of this class is to provide the JavaLanguageVersion, which we know upfront, without having to resolve an
 * entire JDK, which often involves resolution, which causes Gradle to resolve the JavaCompiler/JavaLauncher etc to
 * check if toolchains are enabled. That can cause a dependency cycle, which causes a StackOverflowException.
 * This class breaks that cycle by immediately providing the JavaLanguageVersion without possibly causing a resolution.
 */
final class JavaInstallationMetadataProxy implements InvocationHandler {

    private final JavaLanguageVersion javaLanguageVersion;
    private final Provider<JavaInstallationMetadata> delegate;

    private JavaInstallationMetadataProxy(
            JavaLanguageVersion javaLanguageVersion, Provider<JavaInstallationMetadata> delegate) {
        this.javaLanguageVersion = javaLanguageVersion;
        this.delegate = delegate;
    }

    static JavaInstallationMetadata proxyForVersion(
            JavaLanguageVersion javaLanguageVersion, Provider<JavaInstallationMetadata> delegate) {
        return (JavaInstallationMetadata) Proxy.newProxyInstance(
                JavaInstallationMetadata.class.getClassLoader(),
                new Class[]{JavaInstallationMetadata.class},
                new JavaInstallationMetadataProxy(javaLanguageVersion, delegate));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if ("getLanguageVersion".equals(method.getName())) {
                return javaLanguageVersion;
            } else {
                return method.invoke(delegate.get(), args);
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
