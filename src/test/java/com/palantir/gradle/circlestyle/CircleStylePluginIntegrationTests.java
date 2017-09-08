/*
 * Copyright 2017 Palantir Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.circlestyle;

import static com.google.common.base.Charsets.UTF_8;
import static com.palantir.gradle.circlestyle.TestCommon.copyTestFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Joiner;
import com.google.common.io.Files;

public class CircleStylePluginIntegrationTests {

    @Rule public final EnvironmentVariables env = new EnvironmentVariables();
    @Rule public final TemporaryFolder projectDir = new TemporaryFolder();

    private File reportsDir;

    @Before
    public void setUp() {
        reportsDir = new File(projectDir.getRoot(), "circle/reports");
        env.set("CIRCLE_TEST_REPORTS", reportsDir.toString());
        env.set("TEST_CLASSPATH", pluginClasspath());

        copyTestFile("build.gradle", projectDir, "build.gradle");
        copyTestFile("subproject.gradle", projectDir, "subproject/build.gradle");
        copyTestFile("settings.gradle", projectDir, "settings.gradle");
        copyTestFile("checkstyle.xml", projectDir, "config/checkstyle/checkstyle.xml");
        copyTestFile("findbugsIncludeFilter.xml", projectDir, "config/findbugs/findbugsIncludeFilter.xml");
    }

    @Test
    public void javacIntegrationTest() throws IOException {
        copyTestFile("non-compiling-class", projectDir, "src/main/java/com/example/MyClass.java");

        BuildResult result = GradleRunner.create()
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
        String reportXml = Files.asCharSource(report, UTF_8).read();
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
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "test")
                .buildAndFail();
        assertThat(result.getOutput()).contains("2 tests completed, 1 failed");

        File report = new File(reportsDir, "junit/test/TEST-com.example.MyClassTests.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, UTF_8).read();
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
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "subproject:test")
                .buildAndFail();
        assertThat(result.getOutput()).contains("2 tests completed, 1 failed");

        File report = new File(reportsDir, "junit/subproject/test/TEST-com.example.MyClassTests.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, UTF_8).read();
        assertThat(reportXml)
                .contains("tests=\"2\"")
                .contains("failures=\"1\"")
                .contains("org.junit.ComparisonFailure");
    }

    @Test
    public void checkstyleIntegrationTest() throws IOException {
        copyTestFile("checkstyle-violating-class", projectDir, "src/main/java/com/example/MyClass.java");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "checkstyleMain")
                .buildAndFail();
        assertThat(result.getOutput()).contains("Checkstyle rule violations were found");

        File report = new File(reportsDir, "checkstyle/foobar-checkstyleMain.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, UTF_8).read();
        assertThat(reportXml).contains("Name 'a_constant' must match pattern");
    }

    @Test
    public void findbugsIntegrationTest() throws IOException {
        copyTestFile("findbugs-violating-class", projectDir, "src/main/java/com/example/MyClass.java");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "findbugsMain")
                .buildAndFail();
        assertThat(result.getOutput()).contains("FindBugs rule violations were found");

        File report = new File(reportsDir, "findbugs/foobar-findbugsMain.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, UTF_8).read();
        assertThat(reportXml).contains("methodA() invokes System.exit");
    }

    @Test
    public void buildStepFailureIntegrationTest() throws IOException {
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "failingTask")
                .buildAndFail();
        assertThat(result.getOutput()).contains("This task will always fail");

        File report = new File(reportsDir, "gradle/build.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, UTF_8).read();
        assertThat(reportXml).contains("message=\"RuntimeException: This task will always fail\"");
    }

    @Test
    public void findsUniqueBuildStepsReportFileName() throws IOException {
        assertTrue(new File(reportsDir, "gradle").mkdirs());
        assertTrue(new File(reportsDir, "gradle/build.xml").createNewFile());
        assertTrue(new File(reportsDir, "gradle/build2.xml").createNewFile());

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "failingTask")
                .buildAndFail();
        assertThat(result.getOutput()).contains("This task will always fail");

        File report = new File(reportsDir, "gradle/build3.xml");
        assertThat(report).exists();
        String reportXml = Files.asCharSource(report, UTF_8).read();
        assertThat(reportXml).contains("message=\"RuntimeException: This task will always fail\"");
    }

    @Test
    public void canCallGradleThreeTimesInARow() {
        GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "dependencies")
                .build();
        GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "compileJava")
                .build();
        GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments("--stacktrace", "compileTestJava")
                .build();
    }

    private static String pluginClasspath() {
        URLClassLoader classloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        List<String> classpath = new ArrayList<>();
        for (URL url : classloader.getURLs()) {
            classpath.add(url.getFile());
        }
        return Joiner.on(':').join(classpath);
    }
}
