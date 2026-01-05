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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.junit.WithJdkAutomanagement;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@WithJdkAutomanagement
@DisabledConfigurationCache
class BaselineJavaVersionsIntegrationTest {
    private static final int JAVA_11_BYTECODE = 55;
    private static final int NOT_ENABLE_PREVIEW_BYTECODE = 0;

    private static final String JAVA_11_COMPATIBLE_CODE = """
        import java.util.Optional;

        public class Main {
            public static void main(String[] args) {
                Optional.of(args).isEmpty();
                System.out.println("jdk11 features on runtime " + System.getProperty("java.specification.version"));
            }
        }
        """;

    @BeforeEach
    void beforeEach(RootProject rootProject, SubProject subProject) {
        rootProject.buildGradle().append("""
            allprojects {
                repositories {
                    mavenCentral()
                }
            }

            task runMainClass(type: JavaExec) {
                mainClass = 'Main'
                classpath = sourceSets.main.runtimeClasspath
            }

            jdks {
                daemonTarget = 21
            }
            """);

        rootProject
                .buildGradle()
                .plugins()
                .add("java")
                .add("com.palantir.baseline-java-versions")
                .add("com.palantir.jdks.latest");

        subProject.buildGradle().plugins().add("java");
        subProject.buildGradle().append("""
            tasks.register('printJavaVersionExtension') {
                def extension = project.extensions.javaVersion
                inputs.property 'target', extension.target()
                inputs.property 'runtime', extension.runtime()

                doFirst {
                    println "javaVersion target: ${inputs.properties.target}"
                    println "javaVersion runtime: ${inputs.properties.runtime}"
                }
            }
            """);
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
                    runtime = 21
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printJavaVersionExtension").buildsSuccessfully();

            result.assertThat().output().contains("javaVersion target: 17");
            result.assertThat().output().contains("javaVersion runtime: 21");
        }

        @Test
        void library_target_is_used_when_no_artifacts_are_published_but_project_is_overridden_as_a_library(
                GradleInvoker gradle, RootProject rootProject, SubProject subProject) {

            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                    distributionTarget = 17
                    runtime = 21
                }
                """);

            subProject.buildGradle().append("""
                javaVersion {
                    library()
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printJavaVersionExtension").buildsSuccessfully();

            result.assertThat().output().contains("javaVersion target: 11");
            result.assertThat().output().contains("javaVersion runtime: 21");
        }

        @Test
        void distribution_target_is_used_when_sls_packaging_is_used(
                GradleInvoker gradle, RootProject rootProject, SubProject subProject) {

            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                    distributionTarget = 17
                    runtime = 21
                }
                """);

            subProject.buildGradle().plugins().add("com.palantir.sls-java-service-distribution");

            InvocationResult result =
                    gradle.withArgs("printJavaVersionExtension").buildsSuccessfully();

            result.assertThat().output().contains("javaVersion target: 17");
            result.assertThat().output().contains("javaVersion runtime: 21");
        }
    }

    @Nested
    class Toolchains {
        @Test
        void when_setupJdkToolchains_true_toolchains_are_configured_by_jdks_latest(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersions {
                    javaCompiler = 11
                    libraryTarget = 11
                    runtime = 21
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_11_COMPATIBLE_CODE);

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
                javaVersions {
                    javaCompiler = 11
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
            rootProject.buildGradle().plugins().add("com.palantir.jdks.latest");

            rootProject.mainSourceSet().java().writeClass(JAVA_11_COMPATIBLE_CODE);

            gradle.withArgs(
                            "compileJava",
                            "run",
                            "-Porg.gradle.java.installations.auto-detect=false",
                            "-Porg.gradle.java.installations.auto-download=false")
                    .buildsWithFailure();
        }
    }

    @Nested
    class ExplainJavaVersions {
        @Test
        void explainJavaVersions_prints_the_java_version_used(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersions {
                    javaCompiler = 11
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

    @Nested
    class Verification {
        @Test
        void setting_library_target_to_preview_version_fails(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = '17_PREVIEW'
                }
                """);

            assertThatThrownBy(() -> gradle.withArgs("compileJava").buildsWithFailure())
                    .hasMessageContaining("cannot be run on newer JVMs");
        }
    }

    @Nested
    class AllJavaVersionsUsed {
        @BeforeEach
        void beforeEach(RootProject rootProject) {
            rootProject.buildGradle().append("""
                tasks.register('printAllJavaVersionsUsed') {
                    inputs.property('allJavaVersionsUsed', project.extensions.javaVersions.allJavaVersionsUsed())
                    doLast {
                        println "allJavaVersionsUsed: ${inputs.properties.allJavaVersionsUsed.stream().sorted().toList()}"
                    }
                }
                """);
        }

        @Test
        void gathers_values_from_top_level_javaVersions_extension(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersions {
                    javaCompiler = 25
                    libraryTarget = 11
                    distributionTarget = 17
                    runtime = 21
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [11, 17, 21, 25]");
        }

        @Test
        void gathers_values_from_javaVersion_extensions_in_all_projects_as_well(
                GradleInvoker gradle, RootProject rootProject, SubProject subProject) {

            rootProject.buildGradle().append("""
                javaVersions {
                    javaCompiler = 25
                    libraryTarget = 11
                    distributionTarget = 17
                    runtime = 21
                }
                """);

            subProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 22
                    target = 23
                    runtime = 24
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [11, 17, 21, 22, 23, 24, 25]");
        }

        @Test
        void propagates_task_dependencies(GradleInvoker gradle, RootProject rootProject, SubProject subProject) {
            rootProject.buildGradle().append("""
                import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion

                def generateJavaCompiler = tasks.register('generateJavaCompiler')
                def generateLibraryTarget = tasks.register('generateLibraryTarget')
                def generateDistributionTarget = tasks.register('generateDistributionTarget')
                def generateRuntime = tasks.register('generateRuntime')

                javaVersions {
                    javaCompiler().set(generateJavaCompiler.map { JavaLanguageVersion.of(25) })
                    libraryTarget().set(generateLibraryTarget.map { JavaLanguageVersion.of(11) })
                    distributionTarget().set(generateDistributionTarget.map { ChosenJavaVersion.of(17) })
                    runtime().set(generateRuntime.map { ChosenJavaVersion.of(21) })
                }
                """);

            subProject.buildGradle().append("""
                    import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion

                    def generateJavaCompiler = tasks.register('generateJavaCompiler')
                    def generateTarget = tasks.register('generateTarget')
                    def generateRuntime = tasks.register('generateRuntime')

                    javaVersion {
                        javaCompiler().set(generateJavaCompiler.map { JavaLanguageVersion.of(22) })
                        target().set(generateTarget.map { ChosenJavaVersion.of(23) })
                        runtime().set(generateRuntime.map { ChosenJavaVersion.of(24) })
                    }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [11, 17, 21, 22, 23, 24, 25]");
            result.assertThat().task(":generateJavaCompiler").upToDate();
            result.assertThat().task(":generateLibraryTarget").upToDate();
            result.assertThat().task(":generateDistributionTarget").upToDate();
            result.assertThat().task(":generateRuntime").upToDate();
            result.assertThat().task(":subProject:generateJavaCompiler").upToDate();
            result.assertThat().task(":subProject:generateTarget").upToDate();
            result.assertThat().task(":subProject:generateRuntime").upToDate();
        }

        @Test
        void has_a_value_when_root_javaCompiler_is_not_set(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                    distributionTarget = 17
                    runtime = 21
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [11, 17, 21]");
        }

        @Test
        void has_a_value_when_subproject_javaCompiler_is_not_set(
                GradleInvoker gradle, RootProject rootProject, SubProject subProject) {

            rootProject.buildGradle().append("""
                javaVersions {
                    libraryTarget = 11
                }
                """);

            subProject.buildGradle().append("""
                javaVersion {
                    target = 17
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [11, 17]");
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
