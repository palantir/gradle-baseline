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

package com.palantir.baseline;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class BaselineEncodingIntegrationTest {

    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.baseline-encoding");

        return rootProject.buildGradle().append("""
            sourceCompatibility = 1.8

            repositories {
                mavenLocal()
                mavenCentral()
            }
            """);
    }

    private GradleFile otherEncodingBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java");

        return rootProject.buildGradle().append("""
            sourceCompatibility = 1.8

            repositories {
                mavenLocal()
                mavenCentral()
            }

            tasks.withType(JavaCompile) {
                options.encoding = 'US-ASCII'
            }
            """);
    }

    private static final String javaFile = """
        package test;

        /**
         * Test source file encoding with UTF-8 ☃ Javadoc.
         */
        public class Test {
            private static final String VALUE = "•";
        }
        """;

    @Test
    void compile_java_succeeds_with_baseline_encoding(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.mainSourceSet().java().writeClass(javaFile);

        InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();
        assertThat(result).task(":compileJava").succeeded();
        assertThat(result).output().doesNotContain("unmappable character");
    }

    @Test
    void compile_java_fails_with_other_encoding(GradleInvoker gradle, RootProject rootProject) {
        otherEncodingBuildFile(rootProject);
        rootProject.mainSourceSet().java().writeClass(javaFile);

        InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();
        assertThat(result).task(":compileJava").succeeded();
        assertThat(result).output().contains("unmappable character");
    }
}
