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
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * This test relies on running ./gradlew :gradle-baseline-java-config:publishToMavenLocal.
 */
@GradlePluginTests
class BaselineConfigIntegrationTest {

    private static String getProjectVersion() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                        "git", "describe", "--tags", "--first-parent", "--dirty=.dirty", "--abbrev=7")
                .start();
        process.waitFor();
        return new String(process.getInputStream().readAllBytes()).trim();
    }

    private static GradleFile setupStandardBuildFile(RootProject project) {
        GradleFile buildFile = project.buildGradle();
        buildFile.plugins().add("com.palantir.baseline-config");
        buildFile.append("""
            repositories {
                mavenCentral()
                mavenLocal()
            }
            """);

        return buildFile;
    }

    @Test
    void installs_config(GradleInvoker gradle, RootProject project) throws IOException, InterruptedException {
        String projectVersion = getProjectVersion();

        setupStandardBuildFile(project).append("""
            dependencies {
                // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
                // see ./gradle-baseline-java-config/build.gradle
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
            }
            """, projectVersion);

        gradle.withArgs(
                        "--stacktrace",
                        "--info",
                        "baselineUpdateConfig",
                        "-Pcom.palantir.baseline-format.eclipse",
                        "-Pcom.palantir.baseline-format.palantir-java-format")
                .buildsSuccessfully();

        Path baselineDir = project.path().resolve(".baseline");
        Set<String> actualDirs = Files.list(baselineDir)
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toSet());
        Set<String> expectedDirs = Set.of("checkstyle", "copyright", "eclipse", "idea", "spotless");
        assertThat(actualDirs).isEqualTo(expectedDirs);

        Path projectDir = project.path().resolve("project");
        List<Path> projectDirContents =
                Files.exists(projectDir) ? Files.list(projectDir).collect(Collectors.toList()) : List.of();
        assertThat(projectDirContents).isEmpty();
    }

    @Test
    void gradlew_baseline_update_config_should_still_work_even_if_no_configuration_dependency_is_specified(
            GradleInvoker gradle, RootProject project) throws IOException, InterruptedException {
        String projectVersion = getProjectVersion();

        // forcing is necessary here because Implementation-Version is only available after publish, not during tests
        setupStandardBuildFile(project).append("""
            configurations.baseline {
                resolutionStrategy { force 'com.palantir.baseline:gradle-baseline-java-config:%s' }
            }
            """, projectVersion);

        gradle.withArgs("--stacktrace", "--info", "baselineUpdateConfig").buildsSuccessfully();

        Path baselineDir = project.path().resolve(".baseline");
        List<Path> baselineDirContents = Files.list(baselineDir).collect(Collectors.toList());
        assertThat(baselineDirContents).isNotEmpty();

        Path projectDir = project.path().resolve("project");
        List<Path> projectDirContents =
                Files.exists(projectDir) ? Files.list(projectDir).collect(Collectors.toList()) : List.of();
        assertThat(projectDirContents).isEmpty();
    }

    @Test
    void fails_if_too_many_configuration_dependencies_are_specified(GradleInvoker gradle, RootProject project)
            throws IOException, InterruptedException {
        String projectVersion = getProjectVersion();

        setupStandardBuildFile(project).append("""
            dependencies {
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
                baseline "com.google.guava:guava:21.0"
            }
            """, projectVersion);

        InvocationResult result = gradle.withArgs("--stacktrace", "--info", "baselineUpdateConfig")
                .buildsWithFailure();

        assertThat(result)
                .output()
                .contains("Expected to find exactly one config dependency in the 'baseline' configuration, found: [/");
    }

    @Test
    void gradlew_baseline_update_config_should_be_up_to_date(GradleInvoker gradle, RootProject project)
            throws IOException, InterruptedException {
        String projectVersion = getProjectVersion();

        setupStandardBuildFile(project).append("""
            dependencies {
                // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
                // see ./gradle-baseline-java-config/build.gradle
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
            }
            """, projectVersion);

        gradle.withArgs("--stacktrace", "--info", "baselineUpdateConfig").buildsSuccessfully();

        InvocationResult secondResult = gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();

        assertThat(secondResult).task(":baselineUpdateConfig").upToDate();
    }

    @Test
    void started_pjf_conversion_disables_checkstyle_indentation_module(GradleInvoker gradle, RootProject project)
            throws IOException, InterruptedException {
        String projectVersion = getProjectVersion();

        project.gradlePropertiesFile().appendProperty("com.palantir.baseline-format.palantir-java-format", "started");

        setupStandardBuildFile(project).append("""
            dependencies {
                // NOTE: This only works on Git-clean repositories since it relies on the locally published config artifact,
                // see ./gradle-baseline-java-config/build.gradle
                baseline "com.palantir.baseline:gradle-baseline-java-config:%s@zip"
            }
            """, projectVersion);

        gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();

        Path checkstyleXml = project.path().resolve(".baseline/checkstyle/checkstyle.xml");
        List<String> lines = Files.readAllLines(checkstyleXml);

        assertThat(lines).noneMatch(line -> line.contains("<module name=\"Indentation\">"));
        assertThat(lines).noneMatch(line -> line.contains("<module name=\"ParenPad\">"));
        assertThat(lines).noneMatch(line -> line.contains("<module name=\"LeftCurly\">"));
        assertThat(lines).noneMatch(line -> line.contains("<module name=\"WhitespaceAround\">"));
    }
}
