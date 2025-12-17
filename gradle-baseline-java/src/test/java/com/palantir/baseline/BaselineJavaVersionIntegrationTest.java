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

import com.palantir.baseline.gradlejdks.InheritGradleJdks;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
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
@DisabledConfigurationCache
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

    private static final String JAVA_21_SOURCE_FEATURE_CODE = """
        public class Main {
            sealed interface MyUnion {
                record Foo(int number) implements MyUnion {}
            }

            public static void main(String[] args) {
                MyUnion myUnion = new MyUnion.Foo(1234);
                int ignored = switch (myUnion) {
                    case MyUnion.Foo foo -> foo.number;
                };
                System.out.println("jdk21 features on runtime " + System.getProperty("java.specification.version"));
            }
        }
        """;

    private static final String JAVA_21_API_USAGE = """
        import java.lang.Thread;
        public class Main {
            public static void main(String[] args) {
                // Introduced in JDK 21
                Thread.currentThread().isVirtual();
            }
        }
        """;

    // language=XML
    private static final String CHECKSTYLE_XML = """
        <?xml version="1.0"?>
        <!DOCTYPE module PUBLIC
                "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                "https://checkstyle.org/dtds/configuration_1_3.dtd">

        <module name="Checker">
            <property name="charset" value="UTF-8"/>
            <property name="severity" value="error"/>

            <module name="TreeWalker">
                <module name="ConstantName"/>
            </module>
        </module>
        """;

    @BeforeEach
    void beforeEach(RootProject rootProject) {
        InheritGradleJdks.beforeEach(rootProject);

        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().plugins().add("com.palantir.baseline-java-versions");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            tasks.withType(JavaCompile).configureEach {
                // baseline-module-jvm-args forces all compiler processes to fork, do the same here
                // for representative testing
                options.fork = true
            }

            tasks.register('runMainClass', JavaExec) {
                mainClass = 'Main'
                classpath = sourceSets.main.runtimeClasspath
            }
            """);
    }

    @Nested
    class JavaCompilation {
        @BeforeEach
        void beforeEach(RootProject rootProject) {
            rootProject.buildGradle().append("""
                repositories {
                    mavenLocal()
                }

                dependencies {
                    annotationProcessor "com.palantir.baseline:test-compiler-plugins:${baselineTestCompilerPluginsVersion}"
                }

                tasks.named('compileJava', JavaCompile) {
                    options.compilerArgumentProviders.add({ ['-Xplugin:LogCompilerInfo'] } as CommandLineArgumentProvider)
                }
                """);
        }

        @Nested
        class WithExplicitJavaCompiler {
            @Test
            void java_21_source_feature_fails_targeting_java_17_using_java_21_compiler(
                    GradleInvoker gradle, RootProject rootProject) {

                rootProject.buildGradle().append("""
                    javaVersion {
                        javaCompiler = 21
                        target = 17
                        runtime = 21
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_SOURCE_FEATURE_CODE);

                InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

                result.assertThat().output().contains("error: patterns in switch statements are not supported");

                result.assertThat().output().contains("Compiler Java Version: 21");
                result.assertThat().output().contains("Compiler Arg: --release=17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");
            }

            @Test
            void java_21_api_usage_fails_targeting_java_17_using_java_21_compiler(
                    GradleInvoker gradle, RootProject rootProject) {

                rootProject.buildGradle().append("""
                    javaVersion {
                        javaCompiler = 21
                        target = 17
                        runtime = 21
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_API_USAGE);

                InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

                result.assertThat().output().contains("error: cannot find symbol");

                result.assertThat().output().contains("Compiler Java Version: 21");
                result.assertThat().output().contains("Compiler Arg: --release=17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");
            }

            @Test
            void java_21_api_usage_annoyingly_succeeds_targeting_java_17_with_exports_using_a_java_21_compiler(
                    GradleInvoker gradle, RootProject rootProject) {

                // This test merely shows the behaviour that actually happens - this is not a behaviour we actually
                // want, just something we forced to accept. If you can fix it, you should and instead make this
                // the opposite test.
                // We can't use `--release` with `--add-exports`, which means users can use higher versioned APIs
                // than are available than their target versions, meaning compilation succeeds even when you
                // would not expect it to.

                rootProject.buildGradle().plugins().add("com.palantir.baseline-module-jvm-args");

                rootProject.buildGradle().append("""
                    javaVersion {
                        javaCompiler = 21
                        target = 17
                        runtime = 21
                    }

                    moduleJvmArgs {
                        exports = ['jdk.compiler/com.sun.tools.javac.util']
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_API_USAGE);

                InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();

                result.assertThat().output().contains("Compiler Java Version: 21");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");
                result.assertThat().output().doesNotContain("Compiler Arg: --release=");
                result.assertThat()
                        .output()
                        .contains("Compiler Arg: --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
            }

            @Test
            void java_21_api_usage_fails_targeting_java_17_without_exports_but_module_jvm_args_plugin_21_compiler(
                    GradleInvoker gradle, RootProject rootProject) {

                rootProject.buildGradle().plugins().add("com.palantir.baseline-module-jvm-args");

                rootProject.buildGradle().append("""
                    javaVersion {
                        javaCompiler = 21
                        target = 17
                        runtime = 21
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_API_USAGE);

                InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

                result.assertThat().output().contains("Compiler Java Version: 21");
                result.assertThat().output().contains("Compiler Arg: --release=17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");

                result.assertThat().output().doesNotContain("Compiler Arg: --add-exports");
            }

            @Test
            void java_17_compilation_succeeds_targeting_java_17_using_java_17_compiler(
                    GradleInvoker gradle, RootProject rootProject) {

                rootProject.buildGradle().append("""
                    javaVersion {
                        javaCompiler = 17
                        target = '17'
                        runtime = '17'
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_17_COMPATIBLE_CODE);

                InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();

                result.assertThat().output().contains("Compiler Java Version: 17");
                result.assertThat().output().contains("Compiler Arg: --release=17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");

                File compiledClass = rootProject
                        .buildDir()
                        .path()
                        .resolve("classes/java/main/Main.class")
                        .toFile();
                assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
            }

            @Test
            void checkstyle_runs_with_javaCompiler(GradleInvoker gradle, RootProject rootProject) {
                rootProject.file("config/checkstyle/checkstyle.xml").append(CHECKSTYLE_XML);
                rootProject.buildGradle().plugins().add("checkstyle");

                rootProject.buildGradle().append("""
                    javaVersion {
                        javaCompiler = 21
                        target = 11
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_11_COMPATIBLE_CODE);
                gradle.withArgs("checkstyleMain").buildsSuccessfully();
            }
        }

        /// These tests are mainly to maintain the old behaviour when javaCompiler is not set
        @Nested
        class WithoutExplicitJavaCompiler {
            @Test
            void java_21_source_feature_fails_targeting_java_17(GradleInvoker gradle, RootProject rootProject) {

                rootProject.buildGradle().append("""
                    javaVersion {
                        target = 17
                        runtime = 21
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_SOURCE_FEATURE_CODE);

                InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

                result.assertThat()
                        .output()
                        .contains("error: patterns in switch statements are a preview feature and are disabled");

                result.assertThat().output().contains("Compiler Java Version: 17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");
                result.assertThat().output().doesNotContain("Compiler Arg: --release");
            }

            @Test
            void java_21_api_usage_fails_targeting_java_17(GradleInvoker gradle, RootProject rootProject) {

                rootProject.buildGradle().append("""
                    javaVersion {
                        target = 17
                        runtime = 21
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_API_USAGE);

                InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

                result.assertThat().output().contains("error: cannot find symbol");

                result.assertThat().output().contains("Compiler Java Version: 17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");
                result.assertThat().output().doesNotContain("Compiler Arg: --release");
            }

            @Test
            void java_21_api_usage_fails_targeting_java_17_with_exports(GradleInvoker gradle, RootProject rootProject) {
                // When we add an explicit `javaCompiler`, we need to use `--release` to prevent higher api
                // usage from working, but `--release` is not compatible with `--add-exports`, so we can't use it.
                // Hence, people can use higher APIs with exports and the explicit `javaCompiler`.
                // This test is just recording the old behaviour without explicit `javaCompiler` that stopped
                // higher API usage.

                rootProject.buildGradle().plugins().add("com.palantir.baseline-module-jvm-args");

                rootProject.buildGradle().append("""
                    javaVersion {
                        target = 17
                        runtime = 21
                    }

                    moduleJvmArgs {
                        exports = ['jdk.compiler/com.sun.tools.javac.util']
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_API_USAGE);

                InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

                result.assertThat().output().contains("error: cannot find symbol");

                result.assertThat().output().contains("Compiler Java Version: 17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");
                result.assertThat().output().doesNotContain("Compiler Arg: --release=");
                result.assertThat()
                        .output()
                        .contains("Compiler Arg: --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
            }

            @Test
            void java_21_api_usage_fails_targeting_java_17_without_exports_but_module_jvm_args_plugin(
                    GradleInvoker gradle, RootProject rootProject) {

                rootProject.buildGradle().plugins().add("com.palantir.baseline-module-jvm-args");

                rootProject.buildGradle().append("""
                    javaVersion {
                        target = 17
                        runtime = 21
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_21_API_USAGE);

                InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

                result.assertThat().output().contains("error: cannot find symbol");

                result.assertThat().output().contains("Compiler Java Version: 17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");

                result.assertThat().output().doesNotContain("Compiler Arg: --release");
                result.assertThat().output().doesNotContain("Compiler Arg: --add-exports");
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

                InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();

                result.assertThat().output().contains("Compiler Java Version: 17");
                result.assertThat().output().contains("Compiler Arg: --source=17");
                result.assertThat().output().contains("Compiler Arg: --target=17");
                result.assertThat().output().doesNotContain("Compiler Arg: --release");

                File compiledClass = rootProject
                        .buildDir()
                        .path()
                        .resolve("classes/java/main/Main.class")
                        .toFile();
                assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
            }

            @Test
            void checkstyle_runs_with_target_version(GradleInvoker gradle, RootProject rootProject) {
                rootProject.file("config/checkstyle/checkstyle.xml").append(CHECKSTYLE_XML);
                rootProject.buildGradle().plugins().add("checkstyle");

                rootProject.buildGradle().append("""
                    javaVersion {
                        runtime = 17
                        target = 11
                    }
                    """);

                rootProject.mainSourceSet().java().writeClass(JAVA_11_COMPATIBLE_CODE);
                InvocationResult result = gradle.withArgs("checkstyleMain").buildsWithFailure();
                result.assertThat()
                        .output()
                        .contains("this version of the Java Runtime only recognizes class file versions up to 55.0");
            }
        }
    }

    @Nested
    class JavaExecution {
        @Test
        void java_11_execution_succeeds_on_java_11(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 11
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
                    javaCompiler = 11
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
                    javaCompiler = 17
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
                    javaCompiler = 17
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
    class Javadoc {
        @Test
        void javadoc_works_when_java_compiler_is_same_version_as_target(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 21
                    target = 21
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_21_SOURCE_FEATURE_CODE);

            gradle.withArgs("javadoc").buildsSuccessfully();
        }

        @Test
        void javadoc_works_when_java_compiler_is_a_higher_version_than_the_target(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 21
                    target = 17
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_COMPATIBLE_CODE);

            gradle.withArgs("javadoc").buildsSuccessfully();
        }
    }

    @Nested
    class GroovyCompile {
        @BeforeEach
        void beforeEach(RootProject rootProject) {
            rootProject.buildGradle().plugins().add("groovy");

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 17
                    target = 17
                }

                dependencies {
                    implementation localGroovy()
                }
                """);
        }

        @Test
        void targeting_17_and_setting_java_compiler_to_17_jdk_outputs_17_bytecode(
                GradleInvoker gradle, RootProject rootProject) {

            // The groovy compiler needs to run with the JDK it's targeting, not with
            // whatever we're setting for `javaCompiler` (a Java concept).

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 17
                    target = 17
                }
                """);

            rootProject.mainSourceSet().srcDir("groovy").file("app/Main.groovy").append("""
                println 'hi'
                """);

            gradle.withArgs("compileGroovy").buildsSuccessfully();

            File compiledClass = rootProject
                    .buildDir()
                    .path()
                    .resolve("classes/groovy/main/Main.class")
                    .toFile();

            assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
        }

        @Test
        void targeting_17_and_setting_java_compiler_to_21_outputs_17_bytecode(
                GradleInvoker gradle, RootProject rootProject) {

            // The groovy compiler needs to run with the JDK it's targeting, not with
            // whatever we're setting for `javaCompiler` (a Java concept).

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 21
                    target = 17
                }
                """);

            rootProject.mainSourceSet().srcDir("groovy").file("app/Main.groovy").append("""
                println 'hi'
                """);

            gradle.withArgs("compileGroovy").buildsSuccessfully();

            File compiledClass = rootProject
                    .buildDir()
                    .path()
                    .resolve("classes/groovy/main/Main.class")
                    .toFile();

            assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE);
        }

        @Test
        void targeting_17_and_setting_java_compiler_to_21_does_not_let_you_use_21_jdk_apis(
                GradleInvoker gradle, RootProject rootProject) {

            // The groovy compiler needs to run with the JDK it's targeting, not with
            // whatever we're setting for `javaCompiler` (a Java concept).

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 21
                    target = 17
                }
                """);

            rootProject.mainSourceSet().srcDir("groovy").file("app/Main.groovy").overwrite("""
                import groovy.transform.CompileStatic
                import java.lang.Thread

                @CompileStatic
                class Main {
                    void main() {
                        Thread.currentThread().isVirtual();
                    }
                }
                """);

            InvocationResult result = gradle.withArgs("compileGroovy").buildsWithFailure();

            result.assertThat().output().contains("Cannot find matching method java.lang.Thread#isVirtual()");
        }
    }

    @Nested
    class GradleJavaConfigurationSetup {
        @Test
        void javaPluginConvention_getTargetCompatibility_produces_the_runtime_java_version(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 11
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
                    javaCompiler = 17
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
                    javaCompiler = 17
                    target = '17_PREVIEW'
                }
                """);

            rootProject.mainSourceSet().java().writeClass(JAVA_17_PREVIEW_CODE);

            gradle.withArgs("javadoc").buildsSuccessfully();
        }
    }

    @Nested
    class Verification {
        @Test
        void verification_should_fail_when_target_exceeds_the_runtime_version(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 17
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
                    javaCompiler = 11
                    target = '11_PREVIEW'
                    runtime = '15_PREVIEW'
                }
                """);

            InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

            assertThat(result)
                    .output()
                    .contains("Runtime Java version (15_PREVIEW) must be exactly the same as the compilation target"
                            + " (11_PREVIEW) in root project");
        }

        @Test
        void verification_should_fail_when_runtime_does_not_use_enable_preview_but_target_does(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 17
                    target = '17_PREVIEW'
                    runtime = '17'
                }
                """);

            InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

            assertThat(result)
                    .output()
                    .contains("Runtime Java version (17) must be exactly the same as the compilation target"
                            + " (17_PREVIEW) in root project");
        }

        @Test
        void verification_should_fail_when_java_compiler_is_lower_than_target(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 17
                    target = 21
                    runtime = 21
                }
                """);

            InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

            result.assertThat()
                    .output()
                    .contains("The requested compilation target Java version (21) must not exceed the javaCompiler Java"
                            + " version (17) in root project");
        }

        @Test
        void verification_should_fail_when_preview_target_is_not_the_same_version_as_javaCompiler(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 21
                    target = '17_PREVIEW'
                    runtime = '17_PREVIEW'
                }
                """);

            InvocationResult result = gradle.withArgs("checkJavaVersions").buildsWithFailure();

            result.assertThat()
                    .output()
                    .contains("The version of the Java Compiler (21) must be exactly the same as the compilation target"
                            + " (17_PREVIEW) in root project 'root', because --enable-preview is enabled.");
        }

        @Test
        void verification_should_succeed_when_preview_target_is_the_same_version_as_javaCompiler_and_runtime(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 17
                    target = '17_PREVIEW'
                    runtime = '17_PREVIEW'
                }
                """);

            gradle.withArgs("checkJavaVersions").buildsSuccessfully();
        }

        @Test
        void verification_should_succeed_when_target_and_runtime_versions_match(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 17
                    target = 17
                    runtime = 17
                }
                """);

            gradle.withArgs("checkJavaVersions").buildsSuccessfully();
        }

        @Test
        void verification_should_succeed_when_compilation_is_higher_than_target(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 21
                    target = 17
                    runtime = 17
                }
                """);

            gradle.withArgs("checkJavaVersions").buildsSuccessfully();
        }

        @Test
        void verification_should_succeed_when_compilation_not_set(GradleInvoker gradle, RootProject rootProject) {
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
                    javaCompiler = 11
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
                    javaCompiler = 11
                    target = 11
                    runtime = 11
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
                    javaCompiler = 11
                    target = 11
                    runtime = 11
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
                    javaCompiler = 11
                    target = 11
                    runtime = 11
                }
                """);

            InvocationResult result = gradle.withArgs("check", "--dry-run").buildsSuccessfully();

            assertThat(result).output().contains(":checkRuntimeClasspathCompatible");
        }
    }

    @Nested
    class AllJavaVersionsUsed {
        @BeforeEach
        void beforeEach(RootProject rootProject) {
            rootProject.buildGradle().append("""
                tasks.register('printAllJavaVersionsUsed') {
                    inputs.property('allJavaVersionsUsed', project.extensions.javaVersion.allJavaVersionsUsed())
                    doLast {
                        println "allJavaVersionsUsed: ${inputs.properties.allJavaVersionsUsed.stream().sorted().toList()}"
                    }
                }
                """);
        }

        @Test
        void gathers_target_and_runtime_values(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    javaCompiler = 25
                    target = 17
                    runtime = 21
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [17, 21, 25]");
        }

        @Test
        void propagates_task_dependencies(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                    import com.palantir.baseline.plugins.javaversions.ChosenJavaVersion

                    def generateJavaCompiler = tasks.register('generateJavaCompiler')
                    def generateTarget = tasks.register('generateTarget')
                    def generateRuntime = tasks.register('generateRuntime')

                    javaVersion {
                        javaCompiler().set(generateJavaCompiler.map { JavaLanguageVersion.of(25) })
                        target().set(generateTarget.map { ChosenJavaVersion.of(17) })
                        runtime().set(generateRuntime.map { ChosenJavaVersion.of(21) })
                    }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [17, 21, 25]");
            result.assertThat().task(":generateJavaCompiler").upToDate();
            result.assertThat().task(":generateTarget").upToDate();
            result.assertThat().task(":generateRuntime").upToDate();
        }

        @Test
        void has_a_value_when_javaCompiler_is_not_set(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                javaVersion {
                    target = 17
                    runtime = 21
                }
                """);

            InvocationResult result =
                    gradle.withArgs("printAllJavaVersionsUsed").buildsSuccessfully();

            result.assertThat().output().contains("allJavaVersionsUsed: [17, 21]");
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
