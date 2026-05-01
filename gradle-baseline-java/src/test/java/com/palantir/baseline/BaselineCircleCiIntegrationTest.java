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
import com.palantir.gradle.testing.execution.Options;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class BaselineCircleCiIntegrationTest {

    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java-library").add("com.palantir.baseline-circleci");

        return rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation 'junit:junit:4.12'
            }
            """);
    }

    private static final String javaFile = """
        package test;

        import org.junit.Test;

        public class TestClass {
            @Test
            public void test() {}
        }
        """;

    @Test
    void collects_junit_reports(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.testSourceSet().java().writeClass(javaFile);

        Path testReports = rootProject.path().resolve("circle-reports");
        InvocationResult result = gradle.with(Options.builder()
                        .addArgs("test")
                        .putTestingEnvironmentVariables(
                                "CIRCLE_ARTIFACTS",
                                rootProject.path().resolve("artifacts").toString())
                        .putTestingEnvironmentVariables("CIRCLE_TEST_REPORTS", testReports.toString())
                        .build())
                .buildsSuccessfully();
        assertThat(result).task(":test").succeeded();
        Assertions.assertThat(listDirectory(testReports.resolve("junit").resolve("test")))
                .as("CIRCLE_TEST_REPORTS contains junit XML reports for the :test task")
                .containsExactlyInAnyOrder("TEST-test.TestClass.xml");
    }

    @Test
    void collects_html_reports(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.testSourceSet().java().writeClass(javaFile);

        Path artifacts = rootProject.path().resolve("artifacts");
        InvocationResult result = gradle.with(Options.builder()
                        .addArgs("test")
                        .putTestingEnvironmentVariables("CIRCLE_ARTIFACTS", artifacts.toString())
                        .putTestingEnvironmentVariables(
                                "CIRCLE_TEST_REPORTS",
                                rootProject.path().resolve("circle-reports").toString())
                        .build())
                .buildsSuccessfully();
        assertThat(result).task(":test").succeeded();
        Assertions.assertThat(listDirectory(artifacts.resolve("junit").resolve("test")))
                .as("CIRCLE_ARTIFACTS contains the html report contents for the :test task")
                .containsExactlyInAnyOrder("classes", "css", "index.html", "js", "packages");
    }

    private static List<String> listDirectory(Path path) {
        try (Stream<Path> stream = Files.list(path)) {
            return stream.map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list directory: " + path, e);
        }
    }
}
