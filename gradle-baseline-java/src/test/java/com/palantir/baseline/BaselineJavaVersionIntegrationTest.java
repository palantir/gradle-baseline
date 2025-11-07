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
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineJavaVersionIntegrationTest {
    private static final int JAVA_11_BYTECODE = 55;
    private static final int JAVA_17_BYTECODE = 61;
    private static final int ENABLE_PREVIEW_BYTECODE = 65535;
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

    private static final String JAVA_17_COMPATIBLE_CODE = """
        public class Main {
            sealed interface SealedInterface permits Implementation {}
            record Implementation() implements SealedInterface {}

            public static void main(String[] args) {
                System.out.println("jdk17 features on runtime " + System.getProperty("java.specification.version"));
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
    }

    @Nested
    class JavaCompilation {
        @Test
        void java_17_compilation_fails_targeting_java_11(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 11
                    runtime = 17
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_COMPATIBLE_CODE);

            gradle.withArgs("compileJava").buildsWithFailure();
        }

        @Test
        void java_17_compilation_succeeds_targeting_java_17(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = '17'
                    runtime = '17'
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_COMPATIBLE_CODE);

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
    class JavaExecution {
        @Test
        void java_11_execution_succeeds_on_java_11(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 11
                    runtime = 11
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_11_COMPATIBLE_CODE);

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
                javaVersion {
                    target = 11
                    runtime = 17
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_11_COMPATIBLE_CODE);

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
        void java_17_execution_succeeds_on_java_17(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 17
                    runtime = 17
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_COMPATIBLE_CODE);

            InvocationResult result = gradle.withArgs("runMainClass").buildsSuccessfully();

            assertThat(result).output().contains("jdk17 features on runtime 17");
        }

        @Test
        void java_17_execution_succeeds_on_java_21(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 17
                    runtime = 21
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_COMPATIBLE_CODE);

            InvocationResult result = gradle.withArgs("runMainClass").buildsSuccessfully();

            assertThat(result).output().contains("jdk17 features on runtime 21");

            File compiledClass = rootProject
                    .buildDir()
                    .path()
                    .resolve("classes/java/main/Main.class")
                    .toFile();
            assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
        }
    }

    @Nested
    class GradleJavaConfigurationSetup {
        @Test
        void javaPluginConvention_getTargetCompatibility_produces_the_runtime_java_version(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    target = 11
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

            InvocationResult result =
                    gradle.withArgs("printTargetCompatibility").buildsSuccessfully();

            assertThat(result).output().contains("[[[17]]]");
        }
    }

    @Nested
    class Preview {
        @Test
        void java_17_preview_compilation_works(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = '17_PREVIEW'
                    runtime = '17_PREVIEW'
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

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
                javaVersion {
                    target = '17_PREVIEW'
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

            gradle.withArgs("javadoc", "-i").buildsSuccessfully();
        }
    }

    @Nested
    class Verification {
        @Test
        void verification_should_fail_when_target_exceeds_the_runtime_version(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    target = 17
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
                javaVersion {
                    target = '11_PREVIEW'
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
                javaVersion {
                    target = '17_PREVIEW'
                    runtime = '17'
                }
                """);

            InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

            assertThat(result)
                    .output()
                    .contains("Runtime Java version (17) must be exactly the same as the compilation target"
                            + " (17_PREVIEW)");
        }

        @Test
        void verification_should_succeed_when_target_and_runtime_versions_match(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    target = 17
                    runtime = 17
                }
                """);

            gradle.withArgs("checkJavaVersions").buildsSuccessfully();
        }
    }

    @Nested
    class CheckRuntimeClasspathCompatible {
        @Test
        void fails_when_there_is_a_17_jar_on_the_runtimeClasspath_but_runtime_is_11(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    target = 11
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
        void succeeds_when_all_runtimeClasspath_jars_are_compatible(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 8
                    runtime = 8
                }

                dependencies {
                    implementation 'com.fasterxml.jackson.core:jackson-core:2.16.1'
                }
                """);

            gradle.withArgs("checkRuntimeClasspathCompatible").buildsSuccessfully();
        }

        @Test
        void handles_gradleApi(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 8
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
        void is_a_dependency_of_check(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 11
                    runtime = 11
                }
                """);

            InvocationResult result = gradle.withArgs("check", "--dry-run").buildsSuccessfully();

            assertThat(result).output().contains(":checkRuntimeClasspathCompatible");
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
}
