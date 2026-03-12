/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineTestHeapTest {

    @Test
    void test_default_heap_is_increased(GradleInvoker gradle, RootProject project) {
        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("com.palantir.baseline-test-heap");

        project.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            tasks.withType(Test).configureEach {
                doFirst {
                    logger.lifecycle("Test maxHeapSize: {}", maxHeapSize)
                }
            }
            """);

        // Create a dummy test so the test task actually runs
        project.testSourceSet().java().writeClass("""
            import org.junit.jupiter.api.Test;

            class DummyTest {
                @Test
                void dummyTest() {
                }
            }
            """);

        project.buildGradle().append("""
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
            }
            """);

        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();

        assertThat(result).output().contains("Test maxHeapSize: 2g");
    }

    @Test
    void test_overrides_are_not_impacted(GradleInvoker gradle, RootProject project) {
        project.buildGradle().plugins().add("java");

        project.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            tasks.test {
                maxHeapSize = '1024m'
            }
            """);

        project.buildGradle().plugins().add("com.palantir.baseline-test-heap");

        project.buildGradle().append("""
            tasks.withType(Test).configureEach {
                doFirst {
                    logger.lifecycle("Test maxHeapSize: {}", maxHeapSize)
                }
            }
            """);

        // Create a dummy test so the test task actually runs
        project.testSourceSet().java().writeClass("""
            import org.junit.jupiter.api.Test;

            class DummyTest {
                @Test
                void dummyTest() {
                }
            }
            """);

        project.buildGradle().append("""
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
            }
            """);

        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();

        assertThat(result).output().contains("Test maxHeapSize: 1024m");
    }
}
