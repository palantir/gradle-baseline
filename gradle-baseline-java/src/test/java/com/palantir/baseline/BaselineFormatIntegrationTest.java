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
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
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
@DisabledConfigurationCache
public class BaselineFormatIntegrationTest {

    @SuppressWarnings("for-rollout:deprecation")
    @BeforeEach
    void setup(RootProject rootProject) throws IOException {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                rootProject.directory(".baseline").path().toFile());
        // Disable copyright by default so we can test it individually
        rootProject
                .gradlePropertiesFile()
                .setProperty("com.palantir.baseline-format.copyright", "false")
                .append("""
                    # Required for the eclipse formatter. Delete once it's removed.
                    org.gradle.jvmargs = --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
                    """);
    }

    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.baseline-format");

        return rootProject.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            """);
    }

    private static final String validJavaFile = """
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

    private static final String invalidJavaFile = """
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

    @Test
    void can_apply_plugin(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        gradle.withArgs("format", "--stacktrace").buildsSuccessfully();
    }

    @SuppressWarnings("for-rollout:deprecation")
    @Test
    void eclipse_formatter_integration_test(GradleInvoker gradle, RootProject rootProject) throws IOException {
        File inputDir = new File("src/test/resources/com/palantir/baseline/formatter-in");
        File expectedDir = new File("src/test/resources/com/palantir/baseline/eclipse-formatter-expected");

        File testedDir = rootProject.directory("src/main/java").path().toFile();
        FileUtils.copyDirectory(inputDir, testedDir);

        standardBuildFile(rootProject);
        rootProject.gradlePropertiesFile().appendProperty("com.palantir.baseline-format.eclipse", "true");

        InvocationResult result = gradle.withArgs(":format").buildsSuccessfully();

        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();
        assertThatFilesAreTheSame(testedDir, expectedDir);
    }

    @SuppressWarnings("for-rollout:deprecation")
    @Test
    void palantir_java_format_works(GradleInvoker gradle, RootProject rootProject) throws IOException {
        File inputDir = new File("src/test/resources/com/palantir/baseline/formatter-in");
        File expectedDir = new File("src/test/resources/com/palantir/baseline/palantirjavaformat-expected");

        File testedDir = rootProject.directory("src/main/java").path().toFile();
        FileUtils.copyDirectory(inputDir, testedDir);

        standardBuildFile(rootProject).plugins().add("com.palantir.java-format");

        rootProject.gradlePropertiesFile().appendProperty("com.palantir.baseline-format.palantir-java-format", "true");

        InvocationResult result = gradle.withArgs(":format").buildsSuccessfully();

        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();
        assertThatFilesAreTheSame(testedDir, expectedDir);
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

    @Test
    void can_run_format_task_when_java_plugin_is_missing(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.baseline-format");

        gradle.withArgs("format", "--stacktrace").buildsSuccessfully();
    }

    @Test
    void format_task_works_on_new_source_sets(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            sourceSets { foo }
            """);

        rootProject.file("src/foo/java/test/Test.java").overwrite(invalidJavaFile);

        InvocationResult result = gradle.withArgs("format", "-Pcom.palantir.baseline-format.eclipse")
                .buildsSuccessfully();

        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();
        rootProject.file("src/foo/java/test/Test.java").assertThat().hasContent(validJavaFile);
    }

    @Test
    void format_task_works_on_other_language_java_sources(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.buildGradle().plugins().add("groovy");
        rootProject.buildGradle().append("""
            sourceSets { foo }
            """);

        rootProject.file("src/foo/groovy/test/Test.java").overwrite(invalidJavaFile);

        InvocationResult result = gradle.withArgs("format", "-Pcom.palantir.baseline-format.eclipse")
                .buildsSuccessfully();

        assertThat(result).task(":format").succeeded();
        assertThat(result).task(":spotlessApply").succeeded();
        rootProject.file("src/foo/groovy/test/Test.java").assertThat().hasContent(validJavaFile);
    }

    @Test
    void format_ignores_generated_files(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
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

        rootProject.file("src/generated/java/test/Test.java").overwrite(javaFileContents);

        InvocationResult result = gradle.withArgs("spotlessJavaCheck").buildsSuccessfully();

        assertThat(result).task(":spotlessJava").succeeded();
    }

    @Test
    void formatDiff_updates_only_lines_changed_in_git_diff(GradleInvoker gradle, RootProject rootProject)
            throws InterruptedException, IOException {
        standardBuildFile(rootProject);

        ProcessBuilder gitInit = new ProcessBuilder("git", "init");
        gitInit.directory(rootProject.path().toFile());
        gitInit.start().waitFor();

        ProcessBuilder gitConfigName = new ProcessBuilder("git", "config", "user.name", "Foo");
        gitConfigName.directory(rootProject.path().toFile());
        gitConfigName.start().waitFor();

        ProcessBuilder gitConfigEmail = new ProcessBuilder("git", "config", "user.email", "foo@bar.com");
        gitConfigEmail.directory(rootProject.path().toFile());
        gitConfigEmail.start().waitFor();

        rootProject.file("src/main/java/Main.java").overwrite("""
            class Main {
                public static void crazyExistingFormatting  (  String... args) {

                }
            }
            """);

        ProcessBuilder gitAdd = new ProcessBuilder("git", "add", ".");
        gitAdd.directory(rootProject.path().toFile());
        gitAdd.start().waitFor();

        ProcessBuilder gitCommit = new ProcessBuilder("git", "commit", "-m", "Commit");
        gitCommit.directory(rootProject.path().toFile());
        gitCommit.start().waitFor();

        rootProject.file("src/main/java/Main.java").overwrite("""
            class Main {
                public static void crazyExistingFormatting  (  String... args) {
                                            System.out.println("Reformat me please");
                }
            }
            """);

        gradle.withArgs("formatDiff", "-Pcom.palantir.baseline-format.palantir-java-format")
                .buildsSuccessfully();

        rootProject.file("src/main/java/Main.java").assertThat().hasContent("""
            class Main {
                public static void crazyExistingFormatting  (  String... args) {
                    System.out.println("Reformat me please");
                }
            }
            """);
    }
}
