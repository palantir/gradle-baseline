/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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
class BaselineNullAwayIntegrationTest {

    private static final String VALID_JAVA_FILE = """
        package com.palantir.test;
        public class Test { void test() {} }
        """;

    private static final String INVALID_JAVA_FILE = """
        package com.palantir.test;
        public class Test {
            int test(Throwable throwable) {
                // uh-oh, getMessage may be null!
                return throwable.getMessage().hashCode();
            }
        }
        """;

    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.baseline-java-versions")
                .add("com.palantir.baseline-null-away")
                .add("com.palantir.baseline-error-prone")
                .add("java");

        return rootProject.buildGradle().append("""
            repositories {
                mavenLocal()
                mavenCentral()
            }
            javaVersions {
                javaCompiler = 17
                libraryTarget = 17
            }
            allprojects {
                afterEvaluate {
                    plugins.withId('net.ltgt.errorprone', {
                        tasks.withType(JavaCompile).configureEach({
                          options.errorprone.excludedPaths = null
                          options.compilerArgs += ['-Werror']
                        })
                    })
                }
            }
            """);
    }

    @Test
    void can_apply_plugin(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);

        gradle.withArgs("compileJava", "--info").buildsSuccessfully();
    }

    @Test
    void compileJava_fails_when_null_away_finds_errors(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.mainSourceSet().java().writeClass(INVALID_JAVA_FILE);

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result).output().contains("[NullAway] dereferenced expression throwable.getMessage() is @Nullable");
    }

    @Test
    void test_tasks_are_not_impacted_by_null_away(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.testSourceSet().java().writeClass(INVALID_JAVA_FILE);

        gradle.withArgs("compileTestJava").buildsSuccessfully();
    }

    @Test
    void integration_test_tasks_are_not_impacted_by_null_away(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("org.unbroken-dome.test-sets");

        standardBuildFile(rootProject);

        rootProject.buildGradle().append("""
            testSets {
                integrationTest
            }
            """);

        rootProject.sourceSet("integrationTest").java().writeClass(INVALID_JAVA_FILE);

        gradle.withArgs("compileIntegrationTestJava").buildsSuccessfully();
    }

    @Test
    void compileJava_succeeds_when_null_away_finds_no_errors(GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        rootProject.mainSourceSet().java().writeClass(VALID_JAVA_FILE);

        gradle.withArgs("compileJava").buildsSuccessfully();
    }
}
