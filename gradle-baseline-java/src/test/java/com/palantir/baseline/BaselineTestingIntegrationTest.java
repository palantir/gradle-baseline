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

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class BaselineTestingIntegrationTest {

    // ***DELINEATOR FOR REVIEW: junit4Test
    private static final String JUNIT4_TEST = """
        package test;

        import org.junit.Test;

        public class JUnit4Test {
            @Test
            public void test() {}
        }
        """;

    // ***DELINEATOR FOR REVIEW: junit5Test
    private static final String JUNIT5_TEST = """
        package test;

        import org.junit.jupiter.api.Test;

        public class JUnit5Test {
            @Test
            public void test() {}
        }
        """;

    // ***DELINEATOR FOR REVIEW: jqwikTest
    private static final String JQWIK_TEST = """
        package test;

        import net.jqwik.api.Property;
        import net.jqwik.api.ForAll;

        class JqwikTest {
            @Property
            void test(@ForAll byte value) {}
        }
        """;

    // ***DELINEATOR FOR REVIEW: standardBuildFile
    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java-library").add("com.palantir.baseline-testing");

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

    // ***DELINEATOR FOR REVIEW: runs_JUnit4_tests
    @Test
    void runs_JUnit4_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                testImplementation 'junit:junit'

                testRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JUNIT4_TEST);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        rootProject
                .buildDir()
                .file("reports/tests/test/classes/test.JUnit4Test.html")
                .assertThat()
                .exists();
    }

    // ***DELINEATOR FOR REVIEW: runs_JUnit5_tests
    @Test
    void runs_JUnit5_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        rootProject
                .buildDir()
                .file("reports/tests/test/classes/test.JUnit5Test.html")
                .assertThat()
                .exists();
    }

    // ***DELINEATOR FOR REVIEW: runs_both_JUnit4_and_Junit5_tests
    @Test
    void runs_both_JUnit4_and_Junit5_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                testImplementation 'junit:junit'

                testRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JUNIT4_TEST);
        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        // ***DELINEATOR FOR REVIEW: then
        gradle.withArgs("test").buildsSuccessfully();
        rootProject
                .buildDir()
                .file("reports/tests/test/classes/test.JUnit4Test.html")
                .assertThat()
                .exists();
        rootProject
                .buildDir()
                .file("reports/tests/test/classes/test.JUnit5Test.html")
                .assertThat()
                .exists();
    }

    // ***DELINEATOR FOR REVIEW: runs_Jqwik_tests
    @Test
    void runs_Jqwik_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            dependencies {
                testImplementation 'net.jqwik:jqwik'
            }
            """);

        rootProject.testSourceSet().java().writeClass(JQWIK_TEST);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        rootProject
                .buildDir()
                .file("reports/tests/test/classes/test.JqwikTest.html")
                .assertThat()
                .exists();
    }

    // ***DELINEATOR FOR REVIEW: runs_Nebula_tests
    @Test
    void runs_Nebula_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.buildGradle().plugins().add("groovy");
        rootProject.buildGradle().append("""
            dependencies {
                testImplementation 'com.netflix.nebula:nebula-test'
            }
            """);

        rootProject.file("src/test/groovy/test/Test.groovy").overwrite("""
            package test
            class Test extends spock.lang.Specification {
                def test() {}
            }
            """);

        // ***DELINEATOR FOR REVIEW: then
        gradle.withArgs("test").buildsSuccessfully();
    }

    // ***DELINEATOR FOR REVIEW: runs_test-sets_tests
    @Test
    void runs_test_sets_tests(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.buildGradle().plugins().add("org.unbroken-dome.test-sets");
        rootProject.buildGradle().append("""
            testSets {
                integrationTest
            }
            """);

        rootProject.file("src/integrationTest/java/test/JUnit5Test.java").overwrite(JUNIT5_TEST);

        // ***DELINEATOR FOR REVIEW: then
        gradle.withArgs("integrationTest").buildsSuccessfully();
        rootProject
                .buildDir()
                .file("reports/tests/integrationTest/classes/test.JUnit5Test.html")
                .assertThat()
                .exists();
    }

    // ***DELINEATOR FOR REVIEW: checkJUnitDependencies_JUnit4_without_junit-vintage-engine
    @Test
    void checkJUnitDependencies_JUnit4_without_junit_vintage_engine(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.testSourceSet().java().writeClass(JUNIT4_TEST);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("checkJUnitDependencies").buildsWithFailure();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result)
                .output()
                .contains("Some tests use JUnit4, but the 'test' task is not using the JUnit Vintage engine.");
    }

    // ***DELINEATOR FOR REVIEW: checkJUnitDependencies_JUnit5_without_junit-jupiter
    @Test
    void checkJUnitDependencies_JUnit5_without_junit_jupiter(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject)
                // The junit-jupiter dependency is added automatically by the jvm-test-suite plugin, so it is
                // practically
                // impossible for junit-jupiter to be absent. We manually exclude it here in order to test this case.
                .append("""
                    configurations {
                        testRuntimeClasspath.exclude group: 'org.junit.jupiter', module: 'junit-jupiter'
                    }
                    """);

        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("checkJUnitDependencies").buildsWithFailure();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result)
                .output()
                .contains("Some tests use JUnit5, but the 'test' task is not using the JUnit Jupiter engine.");
    }

    // ***DELINEATOR FOR REVIEW: checkJUnitDependencies_Jqwik_without_jqwik-engine
    @Test
    void checkJUnitDependencies_Jqwik_without_jqwik_engine(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject)
                // jqwik depends on jqwik-engine, so it is practically impossible for jqwik-engine to be absent. We
                // manually exclude it
                // here in order to test this case.
                .append("""
                    dependencies {
                        testImplementation 'net.jqwik:jqwik'
                    }

                    configurations {
                        testRuntimeClasspath.exclude group: 'net.jqwik', module: 'jqwik-engine'
                    }
                    """);

        rootProject.testSourceSet().java().writeClass(JQWIK_TEST);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("checkJUnitDependencies").buildsWithFailure();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result)
                .output()
                .contains("Some tests use Jqwik, but the 'test' task is not using the Jqwik engine.");
    }

    // ***DELINEATOR FOR REVIEW: checkJUnitDependencies_run_as_part_of_check
    @Test
    void checkJUnitDependencies_run_as_part_of_check(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("check").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result).task(":checkJUnitDependencies").succeeded();
    }

    // ***DELINEATOR FOR REVIEW: test_task_without_source_set
    @Test
    void test_task_without_source_set(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject).append("""
            tasks.register('otherTest', Test.class)
            """);

        // ***DELINEATOR FOR REVIEW: then
        // Run a task that will attempt to resolve dependencies
        gradle.withArgs("compileJava").buildsSuccessfully();
    }

    // ***DELINEATOR FOR REVIEW: running_Drecreate_true_will_re-run_tests_even_if_no_code_changes
    @Test
    void running_Drecreate_true_will_rerun_tests_even_if_no_code_changes(
            GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.testSourceSet().java().writeClass(JUNIT5_TEST);

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result = gradle.withArgs("test").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result).task(":test").succeeded();

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result2 = gradle.withArgs("test").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result2).task(":test").upToDate();

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result3 = gradle.withArgs("test", "-Drecreate=true").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result3).task(":test").succeeded();

        // ***DELINEATOR FOR REVIEW: when
        InvocationResult result4 = gradle.withArgs("test", "-Drecreate=true").buildsSuccessfully();

        // ***DELINEATOR FOR REVIEW: then
        assertThat(result4).task(":test").succeeded();
    }

    // ***DELINEATOR FOR REVIEW: does_not_crash_with_non-utf8_resources
    @Test
    void does_not_crash_with_non_utf8_resources(GradleInvoker gradle, RootProject rootProject) throws Exception {
        standardBuildFile(rootProject);

        // Invalid unicode sequence identifier
        java.nio.file.Files.write(
                rootProject.file("src/test/resources/some-binary").path(), new byte[] {(byte) 0xA0, (byte) 0xA1});

        // ***DELINEATOR FOR REVIEW: then
        gradle.withArgs("checkJUnitDependencies").buildsSuccessfully();
    }
}
