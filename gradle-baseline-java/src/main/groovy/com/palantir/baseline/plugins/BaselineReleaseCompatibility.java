/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Optional;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When using JDK 9+ to compile with a targetCompatibility less than JDK 9, this plugin adds compiler arguments per <a
 * href="https://openjdk.java.net/jeps/247">JEP 247</a> to explicitly set the target JDK platform API to maintain binary
 * compatibility.
 *
 * <p>See also <a href="https://github.com/gradle/gradle/issues/2510">Gradle JDK release issue</a>.
 */
public final class BaselineReleaseCompatibility extends AbstractBaselinePlugin {
    private static final Logger log = LoggerFactory.getLogger(BaselineReleaseCompatibility.class);

    @Override
    public void apply(Project project) {
        this.project = project;

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            javaCompile.getOptions().getCompilerArgumentProviders().add(new ReleaseFlagProvider(javaCompile));
        });
    }

    // using a lazy argument provider is crucial because otherwise we'd try to read sourceCompat / targetCompat
    // before the user has even set it in their build.gradle!
    private static final class ReleaseFlagProvider implements CommandLineArgumentProvider {
        private final JavaCompile javaCompile;

        private ReleaseFlagProvider(JavaCompile javaCompile) {
            this.javaCompile = javaCompile;
        }

        @Override
        public Iterable<String> asArguments() {
            JavaVersion jdkVersion = JavaVersion.toVersion(
                    javaCompile.getToolChain().getVersion());
            if (!supportsReleaseFlag(jdkVersion)) {
                log.debug(
                        "BaselineReleaseCompatibility is a no-op for {} in {} as {} doesn't support --release",
                        javaCompile.getName(),
                        javaCompile.getProject(),
                        jdkVersion);
                return Collections.emptyList();
            }

            Optional<JavaVersion> taskTarget =
                    Optional.ofNullable(javaCompile.getTargetCompatibility()).map(JavaVersion::toVersion);

            if (!taskTarget.isPresent()) {
                log.debug(
                        "BaselineReleaseCompatibility is a no-op for {} in {} as no targetCompatibility is set",
                        javaCompile.getName(),
                        javaCompile.getProject());
                return Collections.emptyList();
            }
            JavaVersion target = taskTarget.get();

            if (jdkVersion.compareTo(target) <= 0) {
                log.debug(
                        "BaselineReleaseCompatibility is a no-op for {} in {} as targetCompatibility is higher",
                        javaCompile.getName(),
                        javaCompile.getProject());
                return Collections.emptyList();
            }

            return ImmutableList.of("--release", target.getMajorVersion());
        }

        // The --release flag was added in Java 9: https://openjdk.java.net/jeps/247
        private static boolean supportsReleaseFlag(JavaVersion jdkVersion) {
            return jdkVersion.isJava9Compatible();
        }
    }
}
