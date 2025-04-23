/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public final class GradlePluginTesting implements Extension, ParameterResolver {
    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(Gradlew.class, RootProject.class, SubProject.class);
    private static final Namespace NAMESPACE = Namespace.create(GradlePluginTesting.class);
    private static final Path GRADLE_TESTING_DIR =
            Path.of("build/gradle-testing/").toAbsolutePath();
    private static final String PROJECT_DIR_KEY = "projectDir";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return SUPPORTED_TYPES.contains(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().equals(RootProject.class)) {
            return rootProject(extensionContext);
        }

        if (parameterContext.getParameter().getType().equals(SubProject.class)) {
            return rootProject(extensionContext)
                    .addSubproject(parameterContext.getParameter().getName());
        }

        if (parameterContext.getParameter().getType().equals(Gradlew.class)) {
            return new Gradlew(rootProjectDir(extensionContext));
        }

        throw new IllegalArgumentException(
                "Unsupported parameter type: " + parameterContext.getParameter().getType());
    }

    private static RootProject rootProject(ExtensionContext extensionContext) {
        return new RootProject(rootProjectDir(extensionContext));
    }

    private static Path rootProjectDir(ExtensionContext context) {
        return (Path) context.getStore(NAMESPACE).getOrComputeIfAbsent(PROJECT_DIR_KEY, _ignored -> {
            Path classDir = GRADLE_TESTING_DIR.resolve(
                    Stream.concat(context.getEnclosingTestClasses().stream(), Stream.of(context.getRequiredTestClass()))
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining("/")));
            Path projectDir = classDir.resolve(context.getRequiredTestMethod().getName());
            clearDirectory(projectDir);
            return projectDir;
        });
    }

    private static void clearDirectory(Path projectDir) {
        try {
            FileUtils.deleteDirectory(projectDir.toFile());
            Files.createDirectories(projectDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
