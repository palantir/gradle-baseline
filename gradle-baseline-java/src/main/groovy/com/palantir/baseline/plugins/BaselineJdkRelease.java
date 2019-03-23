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
import java.util.List;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * <p>
 * When using JDK 9+ to compile with a targetCompatibility less than JDK 9, this plugin adds compiler arguments
 * per <a href="https://openjdk.java.net/jeps/247">JEP 247</a> to explicitly set the target JDK platform API
 * to maintain binary compatibility.
 * </p>
 * <p>
 * See also <a href="https://github.com/gradle/gradle/issues/2510">Gradle JDK release issue</a>.
 * </p>
 */
public final class BaselineJdkRelease extends AbstractBaselinePlugin {

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().withPlugin("java", plugin -> {
            project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
                try {
                    configureJavaCompiler(javaCompile);
                } catch (Throwable throwable) {
                    project.getLogger().error("Failed to configure BaselineJdkRelease", throwable);
                }
            });
        });
    }

    private void configureJavaCompiler(JavaCompile javaCompile) {
        JavaVersion sourceCompatibility = JavaVersion.toVersion(javaCompile.getTargetCompatibility());
        JavaVersion targetCompatibility = JavaVersion.toVersion(javaCompile.getTargetCompatibility());
        JavaVersion jdkVersion = JavaVersion.current();

        project.getLogger().debug("Versions:  sourceCompatibility: {}, targetCompatibility: {}, JDK: {}",
                sourceCompatibility,
                targetCompatibility,
                jdkVersion);

        project.getLogger().info("Compiling for targetCompatibility {} using JDK {}", targetCompatibility, jdkVersion);
        if (jdkVersion.compareTo(JavaVersion.VERSION_1_8) > 0
                && jdkVersion.compareTo(targetCompatibility) > 0) {
            // Link against JDK 8 APIs per https://openjdk.java.net/jeps/247
            // see also gradle `jdkLibraryVersion` RFC https://github.com/gradle/gradle/issues/2510
            List<String> additionalArgs = ImmutableList.of("--release", targetCompatibility.getMajorVersion());
            javaCompile.getOptions().getCompilerArgs().addAll(additionalArgs);
            project.getLogger().info("Added compilerArgs: {}, full compilerArgs: {}",
                    additionalArgs, javaCompile.getOptions().getCompilerArgs());
        }
    }

}
