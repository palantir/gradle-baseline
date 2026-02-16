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

package com.palantir.baseline;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class BaselineJavaCompilerHeapTest {

    @Test
    void test_default(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-compiler-heap");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            tasks.withType(JavaCompile).configureEach {
                doFirst {
                    logger.lifecycle("memoryMaximumSize: {}", options.forkOptions.memoryMaximumSize)
                }
            }
            """);

        rootProject.mainSourceSet().java().writeClass("""
            package com.example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """);

        InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();

        assertThat(result).output().contains("memoryMaximumSize: 2g");
    }

    @Test
    void test_overridden(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            tasks.named('compileJava', JavaCompile) {
                options.forkOptions.memoryMaximumSize = '768m'
            }

            tasks.withType(JavaCompile).configureEach {
                doFirst {
                    logger.lifecycle("memoryMaximumSize: {}", options.forkOptions.memoryMaximumSize)
                }
            }
            """);

        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-compiler-heap");

        rootProject.mainSourceSet().java().writeClass("""
            package com.example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
            """);

        InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();

        assertThat(result).output().contains("memoryMaximumSize: 768m");
    }
}
