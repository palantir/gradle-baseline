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
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvocation;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.execution.Options;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.GradleProject;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineCircleCiJavaIntegrationTests {

    private Path reportsDir;

    @BeforeEach
    void setup(RootProject rootProject, SubProject subProject) {
        reportsDir = rootProject.path().resolve("circle/reports");

        rootProject.buildGradle().plugins().add("java").add("com.palantir.baseline-circleci");
        subProject.buildGradle().plugins().add("java");
        for (GradleProject project : new GradleProject[] {rootProject, subProject}) {
            project.buildGradle().append("""
                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation 'junit:junit:4.13.2'
                    testImplementation 'org.assertj:assertj-core:3.27.3'
                }
                """);
        }
    }

    @Test
    void junit_integration_test(GradleInvoker gradle, RootProject rootProject) {
        writeFailingTest(rootProject);

        InvocationResult result = runWithReportsDir(gradle, "test").buildsWithFailure();

        assertThat(result).output().contains("2 tests completed, 1 failed");
        assertReport(reportsDir.resolve("junit/test/TEST-com.example.MyClassTests.xml"));
    }

    @Test
    void junit_subproject_integration_test(GradleInvoker gradle, SubProject subProject) {
        writeFailingTest(subProject);

        InvocationResult result = runWithReportsDir(gradle, ":subProject:test").buildsWithFailure();

        assertThat(result).output().contains("2 tests completed, 1 failed");
        assertReport(reportsDir.resolve("junit/subProject/test/TEST-com.example.MyClassTests.xml"));
    }

    @Test
    void html_reports_written_to_circle_html_test_reports(GradleInvoker gradle, RootProject rootProject) {
        writeFailingTest(rootProject);
        Path htmlReportsDir = rootProject.path().resolve("circle/html-reports");
        Path artifactsDir = rootProject.path().resolve("circle/artifacts");

        gradle.with(Options.builder()
                        .addArgs("test")
                        .putTestingEnvironmentVariables("CIRCLE_HTML_TEST_REPORTS", htmlReportsDir.toString())
                        .putTestingEnvironmentVariables("CIRCLE_ARTIFACTS", artifactsDir.toString())
                        .build())
                .buildsWithFailure();

        assertThat(htmlReportsDir.resolve("junit/test/index.html"))
                .as("HTML test report should be written to $CIRCLE_HTML_TEST_REPORTS")
                .isRegularFile();
        assertThat(artifactsDir.resolve("junit"))
                .as("HTML test report should not be written to $CIRCLE_ARTIFACTS when $CIRCLE_HTML_TEST_REPORTS is set")
                .doesNotExist();
    }

    private GradleInvocation runWithReportsDir(GradleInvoker gradle, String task) {
        return gradle.with(Options.builder()
                .addArgs(task)
                .putTestingEnvironmentVariables("CIRCLE_TEST_REPORTS", reportsDir.toString())
                .build());
    }

    private static void writeFailingTest(GradleProject project) {
        project.mainSourceSet().java().writeClass("""
            package com.example;

            public class MyClass {
                public static int doubleIt(int input) {
                    return 4;
                }

                private MyClass() {}
            }
            """);
        project.testSourceSet().java().writeClass("""
            package com.example;

            import static org.assertj.core.api.Assertions.assertThat;

            import org.junit.Test;

            public class MyClassTests {
                @Test
                public void doublesTwo() {
                    assertThat(MyClass.doubleIt(2)).isEqualTo(4);
                }

                @Test
                public void doublesThree() {
                    assertThat(MyClass.doubleIt(3)).isEqualTo(6);
                }
            }
            """);
    }

    private static void assertReport(Path report) {
        assertThat(report)
                .isRegularFile()
                .content()
                .contains("tests=\"2\"")
                .contains("failures=\"1\"")
                .contains("org.junit.ComparisonFailure");
    }
}
