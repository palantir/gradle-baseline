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

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineFormatIntegrationTest {

    private static final String VALID_JAVA_FILE = """
        package test;

        public class Test {
            void test() {
                int x = 1;
                System.out.println(
                        "Hello");
                Optional.of("hello").orElseGet(() -> {
                    return "Hello World";
                });
            }
        }
        """;

    private static final String INVALID_JAVA_FILE = """
        package test;
        import com.java.unused;
        public class Test { void test() {int x = 1;
            System.out.println(
                "Hello"
            );
            Optional.of("hello").orElseGet(() -> {
                return "Hello World";
            });
        } }
        """;

    @BeforeEach
    void setup(RootProject project) throws IOException {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(project.path().toFile(), ".baseline"));

        // Disable copyright by default so we can test it individually
        project.gradlePropertiesFile()
                .appendProperty("com.palantir.baseline-format.copyright", "false")
                // Required for the eclipse formatter. Delete once it's removed.
                .appendProperty("org.gradle.jvmargs", "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
    }

    @Test
    void can_apply_plugin(GradleInvoker gradle, RootProject project) {
        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("com.palantir.baseline-format");
        project.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            """);

        gradle.withArgs("format", "--stacktrace").buildsSuccessfully();
    }

    @Test
    void eclipse_formatter_integration_test(GradleInvoker gradle, RootProject project) throws IOException {
        File inputDir = new File("src/test/resources/com/palantir/baseline/formatter-in");
        File expectedDir = new File("src/test/resources/com/palantir/baseline/eclipse-formatter-expected");

        File testedDir = new File(project.path().toFile(), "src/main/java");
        FileUtils.copyDirectory(inputDir, testedDir);

        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("com.palantir.baseline-format");
        project.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            """);

        project.gradlePropertiesFile().appendProperty("com.palantir.baseline-format.eclipse", "true");

        InvocationResult result = gradle.withArgs(":format").buildsSuccessfully();
        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();

        assertThatFilesAreTheSame(testedDir, expectedDir);
    }

    @Test
    void palantir_java_format_works(GradleInvoker gradle, RootProject project) throws IOException {
        File inputDir = new File("src/test/resources/com/palantir/baseline/formatter-in");
        File expectedDir = new File("src/test/resources/com/palantir/baseline/palantirjavaformat-expected");

        File testedDir = new File(project.path().toFile(), "src/main/java");
        FileUtils.copyDirectory(inputDir, testedDir);

        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("com.palantir.java-format");
        project.buildGradle().plugins().add("com.palantir.baseline-format");
        project.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            """);

        project.gradlePropertiesFile().appendProperty("com.palantir.baseline-format.palantir-java-format", "true");

        InvocationResult result = gradle.withArgs(":format").buildsSuccessfully();

        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();
        assertThatFilesAreTheSame(testedDir, expectedDir);
    }

    @Test
    void can_run_format_task_when_java_plugin_is_missing(GradleInvoker gradle, RootProject project) {
        project.buildGradle().plugins().add("com.palantir.baseline-format");

        gradle.withArgs("format", "--stacktrace").buildsSuccessfully();
    }

    @Test
    void format_task_works_on_new_source_sets(GradleInvoker gradle, RootProject project) {
        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("com.palantir.baseline-format");
        project.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            sourceSets { foo }
            """);

        var testJavaFile = project.sourceSet("foo").java().writeClass(INVALID_JAVA_FILE);

        InvocationResult result = gradle.withArgs("format", "-Pcom.palantir.baseline-format.eclipse")
                .buildsSuccessfully();
        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();
        assertThat(testJavaFile.text()).isEqualTo(VALID_JAVA_FILE);
    }

    @Test
    void format_task_works_on_other_language_java_sources(GradleInvoker gradle, RootProject project) {
        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("groovy");
        project.buildGradle().plugins().add("com.palantir.baseline-format");
        project.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            sourceSets { foo }
            """);

        var testJavaFile = project.sourceSet("foo")
                .srcDir("groovy")
                .file("test/Test.java")
                .overwrite(INVALID_JAVA_FILE);

        InvocationResult result = gradle.withArgs("format", "-Pcom.palantir.baseline-format.eclipse")
                .buildsSuccessfully();
        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();
        assertThat(testJavaFile.text()).isEqualTo(VALID_JAVA_FILE);
    }

    @Test
    void format_ignores_generated_files(GradleInvoker gradle, RootProject project) {
        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("com.palantir.baseline-format");
        project.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            sourceSets {
                main {
                    java { srcDir 'src/generated/java' }
                }
            }

            // ensure file is in the source set
            sourceSets.main.allJava.filter { it.name == "Test.java" }.singleFile
            """);

        String javaFileContents = """
            package test;
            import java.lang.Void;
            public class Test { Void test() {} }
            """;

        project.directory("src/generated/java/test").file("Test.java").overwrite(javaFileContents);

        InvocationResult result = gradle.withArgs("spotlessJavaCheck").buildsSuccessfully();
        assertThat(result).task(":spotlessJava").succeeded();
    }

    @Test
    void format_diff_updates_only_lines_changed_in_git_diff(GradleInvoker gradle, RootProject project)
            throws IOException, InterruptedException {
        project.buildGradle().plugins().add("java");
        project.buildGradle().plugins().add("com.palantir.baseline-format");
        project.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            """);

        executeCommand("git", "init", project);
        executeCommand("git", "config", "user.name", "Foo", project);
        executeCommand("git", "config", "user.email", "foo@bar.com", project);

        var mainJavaFile = project.mainSourceSet()
                .java()
                .writeClass("""
                    class Main {
                        public static void crazyExistingFormatting  (  String... args) {

                        }
                    }
                    """);

        executeCommand("git", "add", ".", project);
        executeCommand("git", "commit", "-m", "Commit", project);

        mainJavaFile.overwrite("""
            class Main {
                public static void crazyExistingFormatting  (  String... args) {
                                    System.out.println("Reformat me please");
                }
            }
            """);

        gradle.withArgs("formatDiff", "-Pcom.palantir.baseline-format.palantir-java-format")
                .buildsSuccessfully();

        assertThat(mainJavaFile.text()).isEqualTo("""
            class Main {
                public static void crazyExistingFormatting  (  String... args) {
                    System.out.println("Reformat me please");
                }
            }
            """);
    }

    private static void assertThatFilesAreTheSame(File outputDir, File expectedDir) throws IOException {
        Collection<File> files = listJavaFilesRecursively(outputDir);

        for (File file : files) {
            // The files are created inside the `projectDir`
            Path path = file.toPath();
            Path relativized = outputDir.toPath().relativize(path);
            Path expectedFile = expectedDir.toPath().resolve(relativized);
            if (Boolean.getBoolean("recreate")) {
                Files.createDirectories(expectedFile.getParent());
                Files.deleteIfExists(expectedFile);
                Files.copy(path, expectedFile);
            }
            assertThat(path).hasSameTextualContentAs(expectedFile);
        }
    }

    private static Collection<File> listJavaFilesRecursively(File dir) {
        String[] excludedDirectories = {"build", ".gradle", ".baseline"};
        return FileUtils.listFiles(
                dir, new SuffixFileFilter(".java"), new NotFileFilter(new NameFileFilter(excludedDirectories)));
    }

    private static void executeCommand(String command, String arg1, RootProject project)
            throws IOException, InterruptedException {
        new ProcessBuilder(command, arg1)
                .directory(project.path().toFile())
                .start()
                .waitFor();
    }

    private static void executeCommand(String command, String arg1, String arg2, RootProject project)
            throws IOException, InterruptedException {
        new ProcessBuilder(command, arg1, arg2)
                .directory(project.path().toFile())
                .start()
                .waitFor();
    }

    private static void executeCommand(String command, String arg1, String arg2, String arg3, RootProject project)
            throws IOException, InterruptedException {
        new ProcessBuilder(command, arg1, arg2, arg3)
                .directory(project.path().toFile())
                .start()
                .waitFor();
    }
}
