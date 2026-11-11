/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.Options;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineCircleCiHtmlReportsTest {

    @BeforeEach
    void setup(RootProject project) {
        project.buildGradle().plugins().add("java-library").add("com.palantir.baseline-circleci");
        project.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }
            """);
        project.testSourceSet().java().writeClass("""
            package test;

            import org.junit.Test;

            public class TestClass {
                @Test
                public void test() {}
            }
            """);
    }

    @Test
    void prefers_circle_html_test_reports_over_circle_artifacts(GradleInvoker gradle, RootProject project) {
        Path htmlReports = project.path().resolve("html-reports");
        Path artifacts = project.path().resolve("artifacts");

        gradle.with(Options.builder()
                        .addArgs("test")
                        .putTestingEnvironmentVariables("CIRCLE_HTML_TEST_REPORTS", htmlReports.toString())
                        .putTestingEnvironmentVariables("CIRCLE_ARTIFACTS", artifacts.toString())
                        .build())
                .buildsSuccessfully();

        assertThat(htmlReports.resolve("junit/test/index.html")).isRegularFile();
        assertThat(artifacts.resolve("junit")).doesNotExist();
    }

    @Test
    void falls_back_to_circle_artifacts(GradleInvoker gradle, RootProject project) {
        Path artifacts = project.path().resolve("artifacts");

        gradle.with(Options.builder()
                        .addArgs("test")
                        .putTestingEnvironmentVariables("CIRCLE_ARTIFACTS", artifacts.toString())
                        .build())
                .buildsSuccessfully();

        assertThat(artifacts.resolve("junit/test/index.html")).isRegularFile();
    }
}
