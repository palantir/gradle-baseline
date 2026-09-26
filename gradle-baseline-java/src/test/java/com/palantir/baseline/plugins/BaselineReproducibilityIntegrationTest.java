/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineReproducibilityIntegrationTest {

    private static final String HELLO_WORLD_CLASS = """
        public class HelloWorld {
            public static void main(String[] args) {
                System.out.println("Hello World");
            }
        }
        """;

    @Test
    void task_surfaces_the_badness(GradleInvoker gradle, RootProject rootProject) {
        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.baseline-reproducibility")
                .add("java")
                .add("maven-publish");

        rootProject.buildGradle().append("""
            version '1.2.3'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            """);

        rootProject.mainSourceSet().java().writeClass(HELLO_WORLD_CLASS);

        InvocationResult output = gradle.withArgs("check").buildsWithFailure();
        assertThat(output).output().contains("./gradlew :checkExplicitSourceCompatibility --fix");
    }

    @Test
    void task_passes_when_explicitly_set(GradleInvoker gradle, RootProject rootProject) {
        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.baseline-reproducibility")
                .add("java")
                .add("maven-publish");

        rootProject.buildGradle().append("""
            version '1.2.3'

            java {
                sourceCompatibility = 1.8
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            """);

        rootProject.mainSourceSet().java().writeClass(HELLO_WORLD_CLASS);

        gradle.withArgs("checkExplicitSourceCompatibility").buildsSuccessfully();
    }

    @Test
    void no_op_if_nothing_is_published(GradleInvoker gradle, RootProject rootProject) {
        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.baseline-reproducibility")
                .add("java")
                .add("maven-publish");

        rootProject.buildGradle().append("""
            version '1.2.3'
            """);

        rootProject.mainSourceSet().java().writeClass(HELLO_WORLD_CLASS);

        InvocationResult output = gradle.withArgs("check").buildsSuccessfully();
        assertThat(output).output().contains("> Task :checkExplicitSourceCompatibility SKIPPED");
    }

    @Test
    void no_op_if_there_is_not_source(GradleInvoker gradle, RootProject rootProject) {
        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.baseline-reproducibility")
                .add("java")
                .add("maven-publish");

        rootProject.buildGradle().append("""
            version '1.2.3'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            """);

        InvocationResult output = gradle.withArgs("check").buildsSuccessfully();
        assertThat(output).output().contains("> Task :checkExplicitSourceCompatibility SKIPPED");
    }

    @Test
    void task_passes_when_toolchains_are_used(GradleInvoker gradle, RootProject rootProject) {
        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.baseline-reproducibility")
                .add("java")
                .add("maven-publish");

        rootProject.buildGradle().append("""
            version '1.2.3'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
            """);

        rootProject.mainSourceSet().java().writeClass(HELLO_WORLD_CLASS);

        gradle.withArgs("checkExplicitSourceCompatibility").buildsSuccessfully();
    }
}
