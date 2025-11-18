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
import com.palantir.gradle.testing.files.arbitrary.ArbitraryFile;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineTestingIntegrationTest {

    private static final String JUNIT4_TEST = """
        package test;

        import org.junit.Test;

        public class JUnit4Test {
            @Test
            public void test() {}
        }
        """;

    private static final String JUNIT5_TEST = """
        package test;

        import org.junit.jupiter.api.Test;

        public class JUnit5Test {
            @Test
            public void test() {}
        }
        """;

    private static final String JQWIK_TEST = """
        package test;

        import net.jqwik.api.Property;
        import net.jqwik.api.ForAll;

        class JqwikTest {
            @Property
            void test(@ForAll byte value) {}
        }
        """;

    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java-library");
        rootProject.buildGradle().plugins().add("com.palantir.baseline-testing");

        return rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            configurations.all {
                resolutionStrategy {
                    force 'com.netflix.nebula:nebula-test:10.2.0'
                    force 'junit:junit:4.13.2'
                    force 'net.jqwik:jqwik:1.9.2'
                    force 'org.junit.jupiter:junit-jupiter:5.12.0'
                    force 'org.junit.platform:junit-platform-launcher:1.12.0'
                    force 'org.junit.vintage:junit-vintage-engine:5.12.0'
                }
            }
            """);
    }

    @Test
    void runs_junit4_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                testImplementation 'junit:junit'

                testRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JUNIT4_TEST);

        gradle.withArgs("test").buildsSuccessfully();

        assertThat(rootProject
                        .buildDir()
                        .file("reports/tests/test/classes/test.JUnit4Test.html")
                        .path()
                        .toFile())
                .exists();
    }

    @Test
    void runs_junit5_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        gradle.withArgs("test").buildsSuccessfully();

        assertThat(rootProject
                        .buildDir()
                        .file("reports/tests/test/classes/test.JUnit5Test.html")
                        .path()
                        .toFile())
                .exists();
    }

    @Test
    void runs_both_junit4_and_junit5_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                testImplementation 'junit:junit'

                testRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JUNIT4_TEST);
        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        gradle.withArgs("test").buildsSuccessfully();

        assertThat(rootProject
                        .buildDir()
                        .file("reports/tests/test/classes/test.JUnit4Test.html")
                        .path()
                        .toFile())
                .exists();
        assertThat(rootProject
                        .buildDir()
                        .file("reports/tests/test/classes/test.JUnit5Test.html")
                        .path()
                        .toFile())
                .exists();
    }

    @Test
    void runs_jqwik_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                testImplementation 'net.jqwik:jqwik'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JQWIK_TEST);

        gradle.withArgs("test").buildsSuccessfully();

        assertThat(rootProject
                        .buildDir()
                        .file("reports/tests/test/classes/test.JqwikTest.html")
                        .path()
                        .toFile())
                .exists();
    }

    @Test
    void runs_nebula_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        rootProject.buildGradle().plugins().add("groovy");

        rootProject.buildGradle().append("""
            dependencies {
                testImplementation 'com.netflix.nebula:nebula-test'
            }
            """);

        rootProject.file("src/test/groovy/test/Test.groovy").append("""
            package test
            class Test extends spock.lang.Specification {
                def test() {}
            }
            """);

        gradle.withArgs("test").buildsSuccessfully();
    }

    @Test
    void runs_test_sets_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        rootProject.buildGradle().plugins().add("org.unbroken-dome.test-sets");

        rootProject.buildGradle().append("""
            testSets {
                integrationTest
            }
            """);

        rootProject.sourceSet("integrationTest").java().writeClass(JUNIT5_TEST);

        gradle.withArgs("integrationTest").buildsSuccessfully();

        assertThat(rootProject
                        .buildDir()
                        .file("reports/tests/integrationTest/classes/test.JUnit5Test.html")
                        .path()
                        .toFile())
                .exists();
    }

    @Test
    void check_junit_dependencies_junit4_without_junit_vintage_engine(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        rootProject.testSourceSet().java().writeClass(JUNIT4_TEST);

        InvocationResult result = gradle.withArgs("checkJUnitDependencies").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("Some tests use JUnit4, but the 'test' task is not using the JUnit Vintage engine.");
    }

    @Test
    void check_junit_dependencies_junit5_without_junit_jupiter(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        // The junit-jupiter dependency is added automatically by the jvm-test-suite plugin, so it is practically
        // impossible for junit-jupiter to be absent. We manually exclude it here in order to test this case.
        rootProject.buildGradle().append("""
            configurations {
                testRuntimeClasspath.exclude group: 'org.junit.jupiter', module: 'junit-jupiter'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        InvocationResult result = gradle.withArgs("checkJUnitDependencies").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("Some tests use JUnit5, but the 'test' task is not using the JUnit Jupiter engine.");
    }

    @Test
    void check_junit_dependencies_jqwik_without_jqwik_engine(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        // jqwik depends on jqwik-engine, so it is practically impossible for jqwik-engine to be absent. We manually
        // exclude it here in order to test this case.
        rootProject.buildGradle().append("""
            dependencies {
                testImplementation 'net.jqwik:jqwik'
            }

            configurations {
                testRuntimeClasspath.exclude group: 'net.jqwik', module: 'jqwik-engine'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JQWIK_TEST);

        InvocationResult result = gradle.withArgs("checkJUnitDependencies").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("Some tests use Jqwik, but the 'test' task is not using the Jqwik engine.");
    }

    @Test
    void check_junit_dependencies_run_as_part_of_check(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        InvocationResult result = gradle.withArgs("check").buildsSuccessfully();

        assertThat(result).task(":checkJUnitDependencies").succeeded();
    }

    @Test
    void test_task_without_source_set(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            tasks.register('otherTest', Test.class)
            """);

        // Run a task that will attempt to resolve dependencies
        gradle.withArgs("compileJava").buildsSuccessfully();
    }

    @Test
    void running_recreate_true_will_rerun_tests_even_if_no_code_changes(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();
        assertThat(result).task(":test").succeeded();

        InvocationResult result2 = gradle.withArgs("test").buildsSuccessfully();
        assertThat(result2).task(":test").upToDate();

        InvocationResult result3 = gradle.withArgs("test", "-Drecreate=true").buildsSuccessfully();
        assertThat(result3).task(":test").succeeded();

        InvocationResult result4 = gradle.withArgs("test", "-Drecreate=true").buildsSuccessfully();
        assertThat(result4).task(":test").succeeded();
    }

    @Test
    void does_not_crash_with_non_utf8_resources(GradleInvoker gradle, RootProject rootProject) throws Exception {
        standardBuildFile(rootProject);

        ArbitraryFile binaryFile = rootProject.file("src/test/resources/some-binary");

        try (OutputStream os = new FileOutputStream(binaryFile.path().toFile())) {
            // Invalid unicode sequence identifier
            os.write(new byte[] {(byte) 0xA0, (byte) 0xA1});
        }

        gradle.withArgs("checkJUnitDependencies").buildsSuccessfully();
    }
}
