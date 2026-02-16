/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * This test relies on running ./gradlew :gradle-baseline-java-config:publishToMavenLocal.
 */
@GradlePluginTests
@DisabledConfigurationCache
public class BaselineConfigIntegrationTest {
    private static final String PROJECT_VERSION = getProjectVersion();

    private static String getProjectVersion() {
        try {
            ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "describe", "--tags", "--first-parent", "--dirty=.dirty", "--abbrev=7");
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String version = reader.lines().collect(Collectors.joining()).trim();
                process.waitFor();
                return version;
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to get project version from git", e);
        }
    }

    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-config");

        return rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
                mavenLocal()
            }
            """);
    }

    @Test
    public void installs_config(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
                // see ./gradle-baseline-java-config/build.gradle
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
            }
            """, PROJECT_VERSION);

        gradle.withArgs(
                        "--stacktrace",
                        "--info",
                        "baselineUpdateConfig",
                        "-Pcom.palantir.baseline-format.eclipse",
                        "-Pcom.palantir.baseline-format.palantir-java-format")
                .buildsSuccessfully();

        List<String> baselineDirList =
                listDirectory(rootProject.directory(".baseline").path());
        Assertions.assertThat(baselineDirList).containsExactlyInAnyOrder("checkstyle", "copyright", "idea", "spotless");

        List<String> projectDirList =
                listDirectory(rootProject.directory("project").path());
        Assertions.assertThat(projectDirList).isEmpty();
    }

    // is specified
    @Test
    public void gradlew_baseline_update_config_should_still_work_even_if_no_configuration_dependency_is_specified(
            GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        // forcing is necessary here because Implementation-Version is only available after publish, not during tests
        rootProject.buildGradle().append("""
            configurations.baseline {
                resolutionStrategy { force 'com.palantir.baseline:gradle-baseline-java-config:%s' }
            }
            """, PROJECT_VERSION);

        gradle.withArgs("--stacktrace", "--info", "baselineUpdateConfig").buildsSuccessfully();

        List<String> baselineDirList =
                listDirectory(rootProject.directory(".baseline").path());
        Assertions.assertThat(baselineDirList).isNotEmpty();

        List<String> projectDirList =
                listDirectory(rootProject.directory("project").path());
        Assertions.assertThat(projectDirList).isEmpty();
    }

    @Test
    public void fails_if_too_many_configuration_dependencies_are_specified(
            GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
                baseline "com.google.guava:guava:21.0"
            }
            """, PROJECT_VERSION);

        InvocationResult result = gradle.withArgs("--stacktrace", "--info", "baselineUpdateConfig")
                .buildsWithFailure();
        assertThat(result)
                .output()
                .contains("Expected to find exactly one config dependency in the 'baseline' configuration, found: [/");
    }

    @Test
    public void gradlew_baseline_update_config_should_be_up_to_date(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
                // see ./gradle-baseline-java-config/build.gradle
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
            }
            """, PROJECT_VERSION);

        gradle.withArgs("--stacktrace", "--info", "baselineUpdateConfig").buildsSuccessfully();
        InvocationResult secondResult = gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();
        assertThat(secondResult).task(":baselineUpdateConfig").upToDate();
    }

    @SuppressWarnings("for-rollout:deprecation")
    @Test
    public void started_pjf_conversion_disables_checkstyle_indentation_module(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.gradlePropertiesFile().setProperty("com.palantir.baseline-format.palantir-java-format", "started");

        standardBuildFile(rootProject).append("""
            repositories {
                mavenCentral()
                mavenLocal()
            }
            dependencies {
                // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
                // see ./gradle-baseline-java-config/build.gradle
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
            }
            """, PROJECT_VERSION);

        gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();

        String checkstyleXmlContent =
                rootProject.file(".baseline/checkstyle/checkstyle.xml").text();

        Assertions.assertThat(checkstyleXmlContent)
                .doesNotContain("<module name=\"Indentation\">")
                .doesNotContain("<module name=\"ParenPad\">")
                .doesNotContain("<module name=\"LeftCurly\">")
                .doesNotContain("<module name=\"WhitespaceAround\">");
    }

    private List<String> listDirectory(Path path) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return Files.list(path)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list directory: " + path, e);
        }
    }
}
