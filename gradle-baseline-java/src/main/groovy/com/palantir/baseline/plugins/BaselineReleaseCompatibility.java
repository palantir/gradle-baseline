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
import java.util.Optional;
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
public final class BaselineReleaseCompatibility extends AbstractBaselinePlugin {

    @Override
    public void apply(Project project) {
        this.project = project;

        // afterEvaluate is necessary so we only configure tasks _after_ users have had an opportunity to set
        // sourceCompatibility or targetCompatibility
        project.afterEvaluate(p -> {
            project.getTasks().withType(JavaCompile.class).configureEach(this::configureJavaCompileTask);
        });
    }

    private void configureJavaCompileTask(JavaCompile javaCompile) {
        Optional<JavaVersion> taskTarget =
                Optional.ofNullable(javaCompile.getTargetCompatibility()).map(JavaVersion::toVersion);

        if (!taskTarget.isPresent()) {
            project.getLogger().info("BaselineReleaseCompatibility is a no-op for {} in {} as no targetCompatibility "
                    + "is set", javaCompile.getName(), javaCompile.getProject());
            return;
        }

        JavaVersion target = taskTarget.get();
        JavaVersion jdkVersion = JavaVersion.toVersion(javaCompile.getToolChain().getVersion());

        project.getLogger().debug("Compiling for target {} using JDK {}", target, jdkVersion);
        if (supportsReleaseFlag(jdkVersion) && jdkVersion.compareTo(target) > 0) {
            javaCompile.getOptions().getCompilerArgs()
                    .addAll(ImmutableList.of("--release", target.getMajorVersion()));
        }
    }

    // The --release flag was added in Java 9: https://openjdk.java.net/jeps/247
    private static boolean supportsReleaseFlag(JavaVersion jdkVersion) {
        return jdkVersion.compareTo(JavaVersion.VERSION_1_8) > 0;
    }
}
