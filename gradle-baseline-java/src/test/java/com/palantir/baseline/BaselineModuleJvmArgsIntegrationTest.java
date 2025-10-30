/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.execution.TaskOutcome;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineModuleJvmArgsIntegrationTest {

    @BeforeEach
    void beforeEach(RootProject rootProject) {
        rootProject.buildGradle().append("""
            plugins {
                id 'java-library'
                id 'application'
                id 'com.palantir.baseline-java-versions'
                id 'com.palantir.baseline-module-jvm-args'
            }

            application {
                mainClass = 'com.Example'
            }

            javaVersions {
                // Use the same version at the current test runtime so that we definitely have a JDK of this
                // version available for Gradle's built in java toolchains
                libraryTarget = Runtime.version().version().get(0)
            }

            allprojects {
                repositories {
                    mavenCentral()
                    mavenLocal()
                }
            }
            """);
    }

    @Nested
    class Compilation {
        @Test
        void compiles_with_locally_defined_exports(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   exports = ['jdk.compiler/com.sun.tools.javac.code']
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        com.sun.tools.javac.code.Symbol.class.toString();
                    }
                }
                """);

            gradle.withArgs("compileJava").buildsSuccessfully();
        }

        @Test
        void compiles_with_locally_defined_opens(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   opens = ['jdk.compiler/com.sun.tools.javac.code']
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        com.sun.tools.javac.code.Symbol.class.toString();
                    }
                }
                """);

            gradle.withArgs("compileJava").buildsSuccessfully();
        }

        @Test
        void externally_defined_exports_on_annotationProcessor_path_end_up_in_fork_options_not_compiler_options(
                GradleInvoker gradle, RootProject rootProject) {

            createJarWithExport(rootProject, "test.jar");

            rootProject.buildGradle().append("""
                dependencies {
                    annotationProcessor files('test.jar')
                }
                tasks.register('printCompilerArgs') {
                    TaskProvider<JavaCompile> compileJava = tasks.named('compileJava', JavaCompile)
                    mustRunAfter compileJava
                    inputs.property('forkJvmArgs', compileJava.map { it.options.forkOptions.allJvmArgs })
                    inputs.property('compilerArgs', compileJava.map { it.options.allCompilerArgs })
                    doLast {
                        println "forkJvmArgs: ${inputs.properties.forkJvmArgs}"
                        println "compilerArgs: ${inputs.properties.compilerArgs}"
                    }
                }
                """);

            InvocationResult invocationResult =
                    gradle.withArgs("compileJava", "printCompilerArgs").buildsSuccessfully();

            assertThat(invocationResult.output().lines())
                    .as("Expected the export to be on forkOptions args but it was not. The "
                            + "annotationProcessor dep is code running in the compiler, so the *compiler process* "
                            + "needs the --add-exports.")
                    .anySatisfy(line -> {
                        assertThat(line).startsWith("forkJvmArgs:");
                        assertThat(line).contains("--add-exports, java.management/sun.management");
                    });

            assertThat(invocationResult.output().lines())
                    .as("Expected the export to not be on the compilerArgs but it was. The "
                            + "annotationProcessor dep is code running in the compiler, where as exports on "
                            + "compilerArgs change which modules the code under compilation can access. "
                            + "tl;dr it's the wrong place for annotationProcessor deps.")
                    .noneSatisfy(line -> {
                        assertThat(line).startsWith("compilerArgs:");
                        assertThat(line).contains("--add-exports, java.management/sun.management");
                    });
        }

        @Test
        void compiles_with_a_compiler_plugin_requiring_access_to_system_modules_at_the_same_time_as_the_release_args(
                GradleInvoker gradle, RootProject rootProject, SubProject compilerPlugin) {

            compilerPlugin.buildGradle().append("""
                    plugins {
                        id 'java-library'
                        id 'com.palantir.baseline-module-jvm-args'
                    }
                    dependencies {
                        annotationProcessor 'com.google.auto.service:auto-service:1.1.1'
                        compileOnly 'com.google.auto.service:auto-service:1.1.1'
                    }
                    moduleJvmArgs {
                        exports = ['jdk.compiler/com.sun.tools.javac.code']
                    }
                """);

            compilerPlugin.mainSourceSet().java().writeClass("""
                package com;
                import com.sun.source.util.JavacTask;
                import com.sun.source.util.Plugin;
                import com.google.auto.service.AutoService;
                @AutoService(Plugin.class)
                public final class SomePlugin implements Plugin {
                    public String getName() {
                        return "SomePlugin";
                    }
                    public void init(JavacTask task, String... args) {
                        com.sun.tools.javac.code.Symbol.class.toString();
                    }
                }
                """);

            rootProject.buildGradle().append("""
                dependencies {
                    annotationProcessor project(':compilerPlugin')
                }
                tasks.named('compileJava', JavaCompile) {
                    // The compiler plugin requires --add-exports to access types in the compiler module
                    // Previously, this plugin incorrectly put --add-exports on the compilerArgs (rather than
                    // the forkOptions that would apply to the compiler plugin in the compiler context).
                    // --add-exports is by default incompatible with `--release`.
                    options.compilerArgumentProviders.add({ ['--release', '11'] } as CommandLineArgumentProvider)
                    options.compilerArgumentProviders.add({ ['-Xplugin:SomePlugin'] } as CommandLineArgumentProvider)
                }
                """);

            gradle.withArgs("compileJava").buildsSuccessfully();
        }

        @Test
        void compiling_using_the_release_and_add_exports_compiler_args_at_the_same_time_works(
                GradleInvoker gradle, RootProject rootProject) {

            // By default, using the `--release` (added here in the test) and `--add-exports` (added by the
            // plugin under test because we've added asked for a module export in moduleJvmArgs) arguments
            // in compilerArgs will cause a compiler error as it stops you using them together for no real reason.
            //
            // The error we're trying to avoid is:
            //     error: exporting a package from system module jdk.compiler is not allowed with --release
            //
            // This test is checking that we can use them together, with the application of some hackery.

            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   exports = ['jdk.compiler/com.sun.tools.javac.code']
                }
                tasks.named('compileJava', JavaCompile) {
                    options.fork = true
                    options.compilerArgumentProviders.add({ ['--release', '17'] } as CommandLineArgumentProvider)
                    doFirst {
                        println "Fork args: ${options.forkOptions.allJvmArgs}"
                        println "Compiler args: ${options.allCompilerArgs}"
                    }
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        com.sun.tools.javac.code.Symbol.class.toString();
                    }
                }
                """);

            gradle.withArgs("compileJava").buildsSuccessfully();
        }
    }

    @Nested
    class Javadoc {
        @Test
        void builds_javadoc_with_locally_defined_exports(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   exports = ['jdk.compiler/com.sun.tools.javac.code']
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    /**
                     * Javadoc {@link com.sun.tools.javac.code.Symbol}.
                     * @param args Program arguments
                     */
                    public static void main(String[] args) {
                        com.sun.tools.javac.code.Symbol.class.toString();
                    }
                }
                """);

            gradle.withArgs("javadoc").buildsSuccessfully();
        }

        @Test
        void builds_javadoc_with_locally_defined_opens(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   opens = ['jdk.compiler/com.sun.tools.javac.code']
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    /**
                     * Javadoc {@link com.sun.tools.javac.code.Symbol}.
                     * @param args Program arguments
                     */
                    public static void main(String[] args) {
                        com.sun.tools.javac.code.Symbol.class.toString();
                    }
                }
                """);

            gradle.withArgs("javadoc").buildsSuccessfully();
        }
    }

    @Nested
    class ApplicationRun {
        @Test
        void runs_with_locally_defined_exports(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   exports = ['java.management/sun.management']
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        System.out.println(String.join(
                            " ",
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                    }
                }
                """);

            InvocationResult result = gradle.withArgs("run").buildsSuccessfully();

            // Gradle appears to normalize args, joining '--add-exports java.management/sun.management=ALL-UNNAMED'
            // with an equals.
            assertThat(result.output()).contains("--add-exports=java.management/sun.management=ALL-UNNAMED");
        }

        @Test
        void runs_with_locally_defined_exports_with_the_release_plugin_not_toolchains(
                GradleInvoker gradle, RootProject rootProject) {

            rootProject.buildGradle().prepend("""
                plugins {
                    id 'com.palantir.baseline-release-compatibility'
                }
                """);

            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   exports = ['java.management/sun.management']
                }
                sourceCompatibility = 11
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        System.out.println(String.join(
                            " ",
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                    }
                }
                """);

            InvocationResult result = gradle.withArgs("run").buildsSuccessfully();

            // Gradle appears to normalize args, joining '--add-exports java.management/sun.management=ALL-UNNAMED'
            // with an equals.
            assertThat(result.output()).contains("--add-exports=java.management/sun.management=ALL-UNNAMED");
        }

        @Test
        void runs_with_locally_defined_opens(GradleInvoker gradle, RootProject rootProject) {
            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   opens 'java.management/sun.management'
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        System.out.println(String.join(
                            " ",
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                    }
                }
                """);

            InvocationResult result = gradle.withArgs("run").buildsSuccessfully();
            assertThat(result.output()).contains("--add-opens=java.management/sun.management=ALL-UNNAMED");
        }

        @Test
        void executes_with_externally_defined_exports(GradleInvoker gradle, RootProject rootProject) {
            createJarWithExport(rootProject, "test.jar");

            rootProject.buildGradle().append("""
                dependencies {
                    implementation files('test.jar')
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        System.out.println(String.join(
                            " ",
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                    }
                }
                """);

            InvocationResult result = gradle.withArgs("run").buildsSuccessfully();

            // Gradle appears to normalize args, joining '--add-exports java.management/sun.management=ALL-UNNAMED'
            // with an equals.
            assertThat(result.output()).contains("--add-exports=java.management/sun.management=ALL-UNNAMED");
        }
    }

    @Nested
    class JarManifest {
        @Test
        void adds_locally_defined_exports_to_the_jar_manifest(GradleInvoker gradle, RootProject rootProject)
                throws IOException {

            rootProject.buildGradle().append("""
                moduleJvmArgs {
                   exports = ['java.management/sun.management']
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        System.out.println(String.join(
                            " ",
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                    }
                }
                """);

            gradle.withArgs("jar").buildsSuccessfully();

            File libsDir = rootProject.buildDir().path().resolve("libs").toFile();
            JarFile jarFile = Arrays.stream(libsDir.listFiles())
                    .filter(file -> file.getName().endsWith(".jar"))
                    .map(file -> {
                        try {
                            return new JarFile(file);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .findFirst()
                    .orElseThrow();
            String manifestValue = jarFile.getManifest().getMainAttributes().getValue("Add-Exports");
            assertThat(manifestValue).isEqualTo("java.management/sun.management");

            assertThat(jarFile.getManifest().getMainAttributes().containsKey("Baseline-Enable-Preview"))
                    .isFalse();
        }

        @Test
        void adds_baseline_enable_preview_attribute_to_jar_manifest(GradleInvoker gradle, RootProject rootProject)
                throws IOException {

            rootProject.buildGradle().append("""
                javaVersions {
                    runtime = '11_PREVIEW'
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                    }
                }
                """);

            gradle.withArgs("jar").buildsSuccessfully();

            File libsDir = rootProject.buildDir().path().resolve("libs").toFile();
            JarFile jarFile = Arrays.stream(libsDir.listFiles())
                    .filter(file -> file.getName().endsWith(".jar"))
                    .map(file -> {
                        try {
                            return new JarFile(file);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .findFirst()
                    .orElseThrow();
            String manifestValue = jarFile.getManifest().getMainAttributes().getValue("Baseline-Enable-Preview");
            assertThat(manifestValue).isEqualTo("11");
        }

        @Test
        void handles_jars_with_no_manifest(GradleInvoker gradle, RootProject rootProject) {
            String jarName = "test.jar";
            createEmptyJar(rootProject, jarName);

            rootProject.buildGradle().append("""
                dependencies {
                    implementation files('test.jar')
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        System.out.println(String.join(
                            " ",
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                    }
                }
                """);

            InvocationResult result = gradle.withArgs("run").buildsSuccessfully();
            assertThat(result.output()).doesNotContain("--add-exports");
        }

        @Test
        void does_not_add_externally_defined_exports_to_the_jar_manifest(GradleInvoker gradle, RootProject rootProject)
                throws IOException {

            createJarWithExport(rootProject, "test.jar");

            rootProject.buildGradle().append("""
                dependencies {
                    implementation files('test.jar')
                }
                """);

            rootProject.mainSourceSet().java().writeClass("""
                package com;
                public class Example {
                    public static void main(String[] args) {
                        System.out.println(String.join(
                            " ",
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                    }
                }
                """);

            gradle.withArgs("jar").buildsSuccessfully();

            File libsDir = rootProject.buildDir().path().resolve("libs").toFile();
            JarFile jarFile = Arrays.stream(libsDir.listFiles())
                    .filter(file -> file.getName().endsWith(".jar"))
                    .map(file -> {
                        try {
                            return new JarFile(file);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .findFirst()
                    .orElseThrow();
            String manifestValue = jarFile.getManifest().getMainAttributes().getValue("Add-Exports");
            assertThat(manifestValue).isNull();
        }
    }

    @Test
    void validates_exports(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            moduleJvmArgs {
               exports = ['java.management']
            }
            """);

        InvocationResult result = gradle.withArgs("jar").buildsWithFailure();
        assertThat(result.output()).contains("separated by a single slash");
    }

    @Test
    void task_not_up_to_date_when_extension_value_changes(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().append("""
            moduleJvmArgs {
               exports = ['java.management/sun.management']
            }
            """);

        rootProject.mainSourceSet().java().writeClass("""
            package com;
            public class Example {
                public static void main(String[] args) {
                    System.out.println(String.join(
                        " ",
                        java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
                }
            }
            """);

        InvocationResult resultBeforeChange = gradle.withArgs("jar").buildsSuccessfully();

        rootProject
                .buildGradle()
                .edit(content ->
                        content.replace("java.management/sun.management", "java.management/sun.management123"));

        InvocationResult resultAfterChange = gradle.withArgs("jar").buildsSuccessfully();

        assertThat(resultBeforeChange.task(":jar")).hasValueSatisfying(task -> {
            assertThat(task.outcome()).isEqualTo(TaskOutcome.SUCCESS);
        });
        assertThat(resultAfterChange.task(":jar")).hasValueSatisfying(task -> {
            assertThat(task.outcome()).isEqualTo(TaskOutcome.SUCCESS);
        });
    }

    @Test
    void test_task_picks_up_add_exports_from_jars_added_to_classpath_after_configuration(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.buildGradle().append("""
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.2'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.2'
            }
            """);

        rootProject.testSourceSet().java().writeClass("""
            package com;
            import org.junit.jupiter.api.Test;
            class ExampleTest {
                @Test
                void test() {
                    System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                }
            }
            """);

        createJarWithExport(rootProject, "test.jar");

        // Mutate classpath after configuration
        rootProject.buildGradle().append("""
            tasks.named('test').configure {
                classpath += files('test.jar')
                useJUnitPlatform()
                testLogging.showStandardStreams = true
            }
            """);

        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();

        // The test JVM should include the --add-exports argument from the manifest of test-addon.jar
        assertThat(result.output()).contains("--add-exports=java.management/sun.management=ALL-UNNAMED");
    }

    @Test
    void javaexec_task_picks_up_add_exports_from_jars_added_to_classpath_after_configuration(
            GradleInvoker gradle, RootProject rootProject) {

        rootProject.mainSourceSet().java().writeClass("""
            package com;
            public class ExampleMain {
                public static void main(String[] args) {
                    System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                }
            }
            """);

        createJarWithExport(rootProject, "addon.jar");

        // Create a JavaExec task and mutate its classpath after configuration
        rootProject.buildGradle().append("""
            tasks.register('runExample', JavaExec) {
                mainClass = 'com.ExampleMain'
                classpath = sourceSets.main.runtimeClasspath
            }

            // Mutate classpath after configuration
            tasks.named('runExample').configure {
                classpath += files('addon.jar')
            }
            """);

        InvocationResult result = gradle.withArgs("runExample").buildsSuccessfully();
        assertThat(result.output()).contains("--add-exports=java.management/sun.management=ALL-UNNAMED");
    }

    private static void createEmptyJar(RootProject rootProject, String jarName) {
        createEmptyJarWithManifest(rootProject, jarName, new Manifest());
    }

    private void createJarWithExport(RootProject rootProject, String jarName) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Add-Exports", "java.management/sun.management");
        createEmptyJarWithManifest(rootProject, jarName, manifest);
    }

    private static void createEmptyJarWithManifest(RootProject rootProject, String jarName, Manifest manifest) {
        File testJar = rootProject.path().resolve(jarName).toFile();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(testJar), manifest)) {
            // Empty jar
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
