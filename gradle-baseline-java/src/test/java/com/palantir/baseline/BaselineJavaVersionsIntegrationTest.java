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
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.java.JavaFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineJavaVersionsIntegrationTest {
    private static final int JAVA_8_BYTECODE = 52;
    private static final int JAVA_11_BYTECODE = 55;
    private static final int JAVA_17_BYTECODE = 61;
    private static final int ENABLE_PREVIEW_BYTECODE = 65535;
    private static final int NOT_ENABLE_PREVIEW_BYTECODE = 0;

    private JavaFile mainJava;

    private static final String JAVA_8_COMPATIBLE_CODE = """
        public class Main {
            public static void main(String[] args) {
                System.out.println("jdk8 features on runtime " + System.getProperty("java.specification.version"));
            }
        }
        """;

    private static final String JAVA_11_COMPATIBLE_CODE = """
        import java.util.Optional;

        public class Main {
            public static void main(String[] args) {
                Optional.of(args).isEmpty();
                System.out.println("jdk11 features on runtime " + System.getProperty("java.specification.version"));
            }
        }
        """;

    private static final String JAVA_17_PREVIEW_CODE = """
        public class Main {
            sealed interface MyUnion {
                record Foo(int number) implements MyUnion {}
            }

            public static void main(String[] args) {
                MyUnion myUnion = new MyUnion.Foo(1234);
                switch (myUnion) {
                    case MyUnion.Foo foo -> System.out.println("Java 17 pattern matching switch: " + foo.number);
                }
            }
        }
        """;

    @BeforeEach
    void beforeEach(RootProject rootProject) {
        rootProject.buildGradle().append("""
            plugins {
                id 'java'
                id 'com.palantir.baseline-java-versions'
                id 'com.palantir.jdks.latest'
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }

            task runMainClass(type: JavaExec) {
                mainClass = 'Main'
                classpath = sourceSets.main.runtimeClasspath
            }
            """);

        // Fork needed or build fails on circleci with "SystemInfo is not supported on this operating system."
        // Comment out locally in order to get debugging to work
        rootProject.gradlePropertiesFile().append("org.gradle.jvmargs=-Xmx2g\norg.gradle.daemon=true\n");

        mainJava = rootProject.mainSourceSet().java().fileByClassName("Main");
    }

    @Nested
    class LibraryVsDistributionDetection {
        @Test
        void distribution_target_is_used_when_no_artifacts_are_published(
                GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                    distributionTarget = 17
                }
                """);

            mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

            gradle.withArgs("compileJava").buildsSuccessfully();

            File compiledClass = rootProject
                    .buildDir()
                    .path()
                    .resolve("classes/java/main/Main.class")
                    .toFile();
            assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
        }

        @Test
        void library_target_is_used_when_no_artifacts_are_published_but_project_is_overridden_as_a_library(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                    distributionTarget = 17
                }
                javaVersion {
                    library()
                }
                """);

            mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

            gradle.withArgs("compileJava").buildsSuccessfully();

            File compiledClass = rootProject
                    .buildDir()
                    .path()
                    .resolve("classes/java/main/Main.class")
                    .toFile();
            assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
        }

        @Test
        void distribution_target_is_used_when_sls_packaging_is_used(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                apply plugin: 'com.palantir.sls-java-service-distribution'
                javaVersions {
                    libraryTarget = 11
                    distributionTarget = 17
                }
                """);

            mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

            gradle.withArgs("compileJava").buildsSuccessfully();

            File compiledClass = rootProject
                    .buildDir()
                    .path()
                    .resolve("classes/java/main/Main.class")
                    .toFile();
            assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
        }
    }

    @Nested
    class Toolchains {
        @Test
        void when_setupJdkToolchains_true_toolchains_are_configured_by_jdks_latest(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                    runtime = 21
                    setupJdkToolchains = true
                }
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(11)
                        vendor = JvmVendorSpec.ADOPTIUM
                    }
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                        vendor = JvmVendorSpec.ADOPTIUM
                    }
                }
                """);

            mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

            InvocationResult compileJavaResult =
                    gradle.withArgs("compileJava", "--info").buildsSuccessfully();

            assertThat(extractCompileToolchain(compileJavaResult.output())).contains("amazon-corretto-11");

            File compiledClass = rootProject
                    .buildDir()
                    .path()
                    .resolve("classes/java/main/Main.class")
                    .toFile();
            assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);

            InvocationResult runResult =
                    gradle.withArgs("runMainClass", "--info").buildsSuccessfully();

            assertThat(runResult).task(":compileJava").upToDate();
            assertThat(extractRunJavaCommand(runResult.output())).contains("amazon-corretto-21.");

            gradle.withArgs(
                            "compileJava",
                            "run",
                            "-Porg.gradle.java.installations.auto-detect=false",
                            "-Porg.gradle.java.installations.auto-download=false")
                    .buildsSuccessfully();
        }

        @Test
        void when_setupJdkToolchains_false_no_toolchains_are_configured_by_gradle_baseline(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                apply plugin: 'com.palantir.jdks.latest'

                javaVersions {
                    libraryTarget = 11
                    runtime = 21
                    setupJdkToolchains = false
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(11)
                        vendor = JvmVendorSpec.ADOPTIUM
                    }
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                        vendor = JvmVendorSpec.ADOPTIUM
                    }
                }
                """);

            mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

            gradle.withArgs(
                            "compileJava",
                            "run",
                            "-Porg.gradle.java.installations.auto-detect=false",
                            "-Porg.gradle.java.installations.auto-download=false")
                    .buildsWithFailure();
        }

        @Test
        void can_configure_a_jdk_path_to_be_used(GradleInvoker gradle, RootProject rootProject) {
            Assumptions.assumeThat(System.getenv("CI"))
                    .describedAs("This test deletes a directory locally, you don't want to run it on your mac")
                    .isNotNull();

            Path newJavaHome;
            try {
                newJavaHome = Files.createSymbolicLink(
                        rootProject.path().resolve("jdk"), Paths.get(System.getProperty("java.home")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11

                    jdk JavaLanguageVersion.of(11), new JavaInstallationMetadata() {
                        @Override
                        JavaLanguageVersion getLanguageVersion() {
                            return JavaLanguageVersion.of(11)
                        }
                        @Override
                        String getJavaRuntimeVersion() {
                            return '11.0.222'
                        }
                        @Override
                        String getJvmVersion() {
                            return '11.33.44'
                        }
                        @Override
                        String getVendor() {
                            return 'vendor'
                        }
                        @Override
                        Directory getInstallationPath() {
                            return layout.dir(provider { new File('%s') }).get()
                        }
                        @Override
                        boolean isCurrentJvm() {
                            return false
                        }
                    }
                }
                """, newJavaHome);

            rootProject.mainSourceSet().java().fileByPath("Main.java").overwrite(JAVA_11_COMPATIBLE_CODE);

            InvocationResult result =
                    gradle.withArgs("compileJava", "--stacktrace", "--info").buildsSuccessfully();

            assertThat(result).output().contains(newJavaHome.toString());
        }
    }

    @Nested
    class ExplainJavaVersions {
        @Test
        void explainJavaVersions_prints_the_java_version_used(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                    runtime = 17
                }
                """);

            InvocationResult result = gradle.withArgs("explainJavaVersions").buildsSuccessfully();

            assertThat(result).output().contains("target  = 11");
            assertThat(result).output().contains("runtime = 17");
            assertThat(result).output().contains("Reason:");
        }
    }

    private static final int BYTECODE_IDENTIFIER = 0xCAFEBABE;

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    private static void assertBytecodeVersion(
            File file, int expectedMajorBytecodeVersion, int expectedMinorBytecodeVersion) {
        try (InputStream stream = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt();
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode");
            }
            int minorBytecodeVersion = 0xFFFF & dis.readShort();
            int majorBytecodeVersion = 0xFFFF & dis.readShort();

            assertThat(majorBytecodeVersion).isEqualTo(expectedMajorBytecodeVersion);
            assertThat(minorBytecodeVersion).isEqualTo(expectedMinorBytecodeVersion);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String extractCompileToolchain(String output) {
        Matcher compileMatcher = Pattern.compile("^Compiling with toolchain '([^']*)'", Pattern.MULTILINE)
                .matcher(output);
        if (!compileMatcher.find()) {
            throw new IllegalStateException("Could not find compile toolchain in output");
        }
        return compileMatcher.group(1);
    }

    private static String extractRunJavaCommand(String output) {
        Matcher matcher = Pattern.compile("^Starting process 'command '([^']*)/bin/java''.*Main", Pattern.MULTILINE)
                .matcher(output);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not find run java command in output");
        }
        return matcher.group(1);
    }
}
