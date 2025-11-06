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
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineJavaVersionIntegrationTest {
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

    @Test
    void java_11_compilation_fails_targeting_java_8(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 8
                runtime = 11
            }
            """);

        mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

        gradle.withArgs("compileJava").buildsWithFailure();
    }

    @Test
    void distribution_target_is_used_when_no_artifacts_are_published(GradleInvoker gradle, RootProject rootProject) {
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
    void java_17_preview_compilation_works(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 11
                distributionTarget = '17_PREVIEW'
            }
            """);

        mainJava.overwrite(JAVA_17_PREVIEW_CODE);

        gradle.withArgs("compileJava", "-i").buildsSuccessfully();

        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, ENABLE_PREVIEW_BYTECODE);
    }

    @Test
    void setting_library_target_to_preview_version_fails(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '17_PREVIEW'
            }
            """);

        mainJava.overwrite(JAVA_17_PREVIEW_CODE);

        InvocationResult result = gradle.withArgs("compileJava", "-i").buildsWithFailure();

        assertThat(result).output().contains("cannot be run on newer JVMs");
    }

    @Test
    void java_17_preview_on_single_project_works(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersion {
                runtime = '17_PREVIEW'
                target = '17_PREVIEW'
            }
            """);

        mainJava.overwrite(JAVA_17_PREVIEW_CODE);

        gradle.withArgs("compileJava", "-i").buildsSuccessfully();

        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, ENABLE_PREVIEW_BYTECODE);
    }

    @Test
    void java_17_preview_javadoc_works(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 11
                distributionTarget = '17_PREVIEW'
            }
            """);

        mainJava.overwrite(JAVA_17_PREVIEW_CODE);

        gradle.withArgs("javadoc", "-i").buildsSuccessfully();
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

    @Test
    void java_11_compilation_succeeds_targeting_java_11(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = '11'
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
    void java_11_execution_succeeds_on_java_11(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 11
            }
            """);

        mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

        InvocationResult result = gradle.withArgs("runMainClass").buildsSuccessfully();

        assertThat(result).output().contains("jdk11 features on runtime 11");

        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
    }

    @Test
    void java_11_execution_succeeds_on_java_17(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 11
                runtime = 17
            }
            """);

        mainJava.overwrite(JAVA_11_COMPATIBLE_CODE);

        InvocationResult result = gradle.withArgs("runMainClass").buildsSuccessfully();

        assertThat(result).output().contains("jdk11 features on runtime 17");

        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
    }

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

        InvocationResult runResult = gradle.withArgs("runMainClass", "--info").buildsSuccessfully();

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
    void java_8_execution_succeeds_on_java_8(GradleInvoker gradle, RootProject rootProject) {
        Assumptions.assumeThat(System.getProperty("os.arch"))
                .describedAs(
                        "On an M1 mac, this test will fail to download"
                            + " https://api.adoptopenjdk.net/v3/binary/latest/8/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk")
                .isNotEqualTo("aarch64");

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 8
            }
            """);

        mainJava.overwrite(JAVA_8_COMPATIBLE_CODE);

        InvocationResult result = gradle.withArgs("runMainClass").buildsSuccessfully();

        assertThat(result).output().contains("jdk8 features on runtime 1.8");
    }

    @Test
    void java_8_execution_succeeds_on_java_11(GradleInvoker gradle, RootProject rootProject) {
        Assumptions.assumeThat(System.getProperty("os.arch"))
                .describedAs(
                        "On an M1 mac, this test will fail to download"
                            + " https://api.adoptopenjdk.net/v3/binary/latest/8/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk")
                .isNotEqualTo("aarch64");

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 8
                runtime = 11
            }
            """);

        mainJava.overwrite(JAVA_8_COMPATIBLE_CODE);

        InvocationResult result = gradle.withArgs("runMainClass").buildsSuccessfully();

        assertThat(result).output().contains("jdk8 features on runtime 11");

        File compiledClass = rootProject
                .buildDir()
                .path()
                .resolve("classes/java/main/Main.class")
                .toFile();
        assertBytecodeVersion(compiledClass, JAVA_8_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
    }

    @Test
    void javaPluginConvention_getTargetCompatibility_produces_the_runtime_java_version(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 11
                runtime = 17
            }
            task printTargetCompatibility() {
                doLast {
                    System.out.println("[[[" + project.getConvention()
                    .getPlugin(org.gradle.api.plugins.JavaPluginConvention.class)
                    .getTargetCompatibility() + "]]]")
                }
            }
            """);

        InvocationResult result = gradle.withArgs("printTargetCompatibility").buildsSuccessfully();

        assertThat(result).output().contains("[[[17]]]");
    }

    @Test
    void verification_should_fail_when_target_exceeds_the_runtime_version(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 17
                runtime = 11
            }
            """);

        InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

        assertThat(result).output().contains("The requested compilation target");
    }

    @Test
    void verification_should_fail_when_enable_preview_is_on_but_versions_differ(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            javaVersions {
                distributionTarget = '11_PREVIEW'
                runtime = '15_PREVIEW'
            }
            """);

        InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("Runtime Java version (15_PREVIEW) must be exactly the same as the compilation target"
                        + " (11_PREVIEW)");
    }

    @Test
    void verification_should_fail_when_runtime_does_not_use_enable_preview_but_compilation_does(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            javaVersions {
                distributionTarget = '17_PREVIEW'
                runtime = '17'
            }
            """);

        InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("Runtime Java version (17) must be exactly the same as the compilation target (17_PREVIEW)");
    }

    @Test
    void verification_should_succeed_when_target_and_runtime_versions_match(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 17
                runtime = 17
            }
            """);

        gradle.withArgs("checkJavaVersions").buildsSuccessfully();
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

        InvocationResult result = gradle.withArgs("compileJava", "--stacktrace").buildsSuccessfully();

        assertThat(result).output().contains(newJavaHome.toString());
    }

    @Test
    void checkRuntimeClasspathCompatible_fails_when_there_is_a_17_jar_on_the_runtimeClasspath_but_runtime_is_11(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 11
                runtime = 11
            }

            configurations {
                java17jar
            }

            dependencies {
                // This has java 17 class files and is a multi-release jar with java 21 class files
                java17jar 'org.springframework:spring-core:6.1.5'
                implementation files(configurations.java17jar)
            }
            """);

        InvocationResult result =
                gradle.withArgs("checkRuntimeClasspathCompatible").buildsWithFailure();

        assertThat(result).output().contains("spring-core-6.1.5.jar");
        assertThat(result).output().contains("spring-jcl-6.1.5.jar");
        assertThat(result).output().contains("bytecode major version 61");
    }

    @Test
    void
            checkRuntimeClasspathCompatible_succeeds_when_there_is_only_jars_of_the_compatible_java_runtime_versions_on_the_runtimeClasspath(
                    GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 8
                runtime = 8
            }

            dependencies {
                implementation 'com.fasterxml.jackson.core:jackson-core:2.16.1'
            }
            """);

        gradle.withArgs("checkRuntimeClasspathCompatible").buildsSuccessfully();
    }

    @Test
    void checkRuntimeClasspathCompatible_handles_gradleApi(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 8
                runtime = 8
            }

            dependencies {
                // this has relocated multi-version jar classes that have not been put in the right place (at least
                // for the versions of gradle used when tested). eg:
                // gradle-api-7.5.1.jar: org/gradle/internal/impldep/META-INF/versions/9/module-info.class has bytecode major version 53
                implementation gradleApi()
            }
            """);

        gradle.withArgs("checkRuntimeClasspathCompatible", "--write-locks").buildsSuccessfully();
    }

    @Test
    void checkRuntimeClasspathCompatible_is_a_dependency_of_check(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            javaVersions {
                libraryTarget = 11
                runtime = 11
            }
            """);

        InvocationResult result = gradle.withArgs("check", "--dry-run").buildsSuccessfully();

        assertThat(result).output().contains(":checkRuntimeClasspathCompatible");
    }

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
