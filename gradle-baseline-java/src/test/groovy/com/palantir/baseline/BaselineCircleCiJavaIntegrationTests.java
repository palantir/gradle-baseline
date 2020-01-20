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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.TemporaryFolder;

public class BaselineCircleCiJavaIntegrationTests {

    @Rule
    public final EnvironmentVariables env = new EnvironmentVariables();

    @Rule
    public final TemporaryFolder projectDir = new TemporaryFolder();

    private File reportsDir;

    @Before
    public void before() {
        reportsDir = new File(projectDir.getRoot(), "circle/reports");
        env.set("CIRCLE_TEST_REPORTS", reportsDir.toString());

        copyTestFile("build.gradle", projectDir, "build.gradle");
        copyTestFile("subproject.gradle", projectDir, "subproject/build.gradle");
        copyTestFile("settings.gradle", projectDir, "settings.gradle");
        copyTestFile("checkstyle.xml", projectDir, "config/checkstyle/checkstyle.xml");
    }

    @Test
    public void javacIntegrationTest() throws IOException {
        copyTestFile("non-compiling-class", projectDir, "src/main/java/com/example/MyClass.java");

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "compileJava")
                .buildAndFail();
        assertThat(result.getOutput())
                .contains("Compilation failed")
                .contains("error: incompatible types")
                .contains("private final int a")
                .contains("error: cannot assign a value to final variable b")
                .contains("b = 2")
                .contains("uses unchecked or unsafe operations");

        File report = new File(reportsDir, "javac/foobar-compileJava.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, StandardCharsets.UTF_8).read();
        assertThat(reportXml)
                .contains("incompatible types")
                .contains("private final int a")
                .contains("cannot assign a value to final variable b")
                .contains("b = 2")
                .doesNotContain("uses unchecked or unsafe operations");
    }

    @Test
    public void junitIntegrationTest() throws IOException {
        copyTestFile("tested-class", projectDir, "src/main/java/com/example/MyClass.java");
        copyTestFile("tested-class-tests", projectDir, "src/test/java/com/example/MyClassTests.java");

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "test")
                .buildAndFail();
        assertThat(result.getOutput()).contains("2 tests completed, 1 failed");

        File report = new File(reportsDir, "junit/test/TEST-com.example.MyClassTests.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, StandardCharsets.UTF_8).read();
        assertThat(reportXml)
                .contains("tests=\"2\"")
                .contains("failures=\"1\"")
                .contains("org.junit.ComparisonFailure");
    }

    @Test
    public void junitSubprojectIntegrationTest() throws IOException {
        copyTestFile("tested-class", projectDir, "subproject/src/main/java/com/example/MyClass.java");
        copyTestFile("tested-class-tests", projectDir, "subproject/src/test/java/com/example/MyClassTests.java");

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "subproject:test")
                .buildAndFail();
        assertThat(result.getOutput()).contains("2 tests completed, 1 failed");

        File report = new File(reportsDir, "junit/subproject/test/TEST-com.example.MyClassTests.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, StandardCharsets.UTF_8).read();
        assertThat(reportXml)
                .contains("tests=\"2\"")
                .contains("failures=\"1\"")
                .contains("org.junit.ComparisonFailure");
    }

    @Test
    public void checkstyleIntegrationTest() throws IOException {
        copyTestFile("checkstyle-violating-class", projectDir, "src/main/java/com/example/MyClass.java");

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "checkstyleMain")
                .buildAndFail();
        assertThat(result.getOutput()).contains("Checkstyle rule violations were found");

        File report = new File(reportsDir, "checkstyle/foobar-checkstyleMain.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, StandardCharsets.UTF_8).read();
        assertThat(reportXml).contains("Name 'a_constant' must match pattern");
    }

    @Test
    public void buildStepFailureIntegrationTest() throws IOException {
        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "failingTask")
                .buildAndFail();
        assertThat(result.getOutput()).contains("This task will always fail");

        File report = new File(reportsDir, "gradle/build.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, StandardCharsets.UTF_8).read();
        assertThat(reportXml).contains("message=\"RuntimeException: This task will always fail\"");
    }

    @Test
    public void findsUniqueBuildStepsReportFileName() throws IOException {
        assertThat(new File(reportsDir, "gradle").mkdirs()).isTrue();
        assertThat(new File(reportsDir, "gradle/build.xml").createNewFile()).isTrue();
        assertThat(new File(reportsDir, "gradle/build2.xml").createNewFile()).isTrue();

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "failingTask")
                .buildAndFail();
        assertThat(result.getOutput()).contains("This task will always fail");

        File report = new File(reportsDir, "gradle/build3.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, StandardCharsets.UTF_8).read();
        assertThat(reportXml).contains("message=\"RuntimeException: This task will always fail\"");
    }

    @Test
    public void canCallGradleThreeTimesInARow() {
        GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "dependencies")
                .build();
        GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "compileJava")
                .build();
        GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "compileTestJava")
                .build();
    }

    private static void copyTestFile(String source, TemporaryFolder root, String target) {
        File targetFile = new File(root.getRoot(), target);
        targetFile.getParentFile().mkdirs();
        try (OutputStream stream = new FileOutputStream(targetFile)) {
            Resources.copy(Resources.getResource(BaselineCircleCiJavaIntegrationTests.class, source), stream);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
