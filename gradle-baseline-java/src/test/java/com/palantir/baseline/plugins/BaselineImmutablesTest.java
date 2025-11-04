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

package com.palantir.baseline.plugins;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@GradlePluginTests
class BaselineImmutablesTest {
    private static final String IMMUTABLES = "org.immutables:value:2.8.8";
    private static final String IMMUTABLES_ANNOTATIONS = IMMUTABLES + ":annotations";

    @BeforeEach
    void setup(RootProject project) {
        project.buildGradle().append("""
            plugins {
                id 'java-library'
                id 'org.unbroken-dome.test-sets'
                id 'com.palantir.baseline-immutables'
                id 'com.palantir.jdks.latest'
            }

            repositories {
                mavenCentral()
            }

            javaVersions {
                libraryTarget = 17
            }

            task compileAll

            tasks.withType(JavaCompile) { javaCompile ->
                doFirst {
                    logger.lifecycle "Debug compiler args: ${javaCompile.name}: ${javaCompile.options.allCompilerArgs}"
                    logger.lifecycle "Debug compiler fork args: ${javaCompile.name}: ${javaCompile.options.forkOptions.allJvmArgs}"
                    logger.lifecycle "Debug compiler fork: ${javaCompile.name}: ${javaCompile.options.fork}"
                }

                tasks.compileAll.dependsOn javaCompile
            }
            """);
    }

    @Test
    void inserts_incremental_compilation_args_into_source_sets_that_have_immutables(
            GradleInvoker gradle, RootProject project) {
        project.buildGradle().append("""
            testSets {
                hasImmutables
                doesNotHaveImmutables
                hasImmutablesAddedInAfterEvaluate
                onlyHasImmutablesAnnotations
            }

            afterEvaluate {
                dependencies {
                    hasImmutablesAddedInAfterEvaluateAnnotationProcessor '%s'
                }
            }

            dependencies {
                annotationProcessor '%s'

                hasImmutablesAnnotationProcessor '%s'

                onlyHasImmutablesAnnotationsAnnotationProcessor '%s'
            }
            """, IMMUTABLES, IMMUTABLES, IMMUTABLES, IMMUTABLES_ANNOTATIONS);

        project.mainSourceSet().java().writeClass("""
            public class Foo {}
            """);

        project.sourceSet("hasImmutables").java().writeClass("""
            public class Foo {}
            """);

        project.sourceSet("doesNotHaveImmutables").java().writeClass("""
            public class Foo {}
            """);

        project.sourceSet("hasImmutablesAddedInAfterEvaluate").java().writeClass("""
            public class Foo {}
            """);

        project.sourceSet("onlyHasImmutablesAnnotations").java().writeClass("""
            public class Foo {}
            """);

        InvocationResult result = gradle.withArgs("compileAll").buildsSuccessfully();

        assertThat(result).output().contains("compileJava: [-Aimmutables.gradle.incremental]");
        assertThat(result).output().contains("compileHasImmutablesJava: [-Aimmutables.gradle.incremental]");
        assertThat(result).output().contains("compileDoesNotHaveImmutablesJava: []");
        assertThat(result)
                .output()
                .contains("compileHasImmutablesAddedInAfterEvaluateJava: [-Aimmutables.gradle.incremental]");
        assertThat(result).output().contains("compileOnlyHasImmutablesAnnotationsJava: []");
    }

    @ParameterizedTest
    @ValueSource(ints = {11, 17})
    void compatible_with_java(int javaVersion, GradleInvoker gradle, RootProject project) {
        // Context: https://github.com/immutables/immutables/issues/1379#issuecomment-1254224741

        project.buildGradle().append("""
            plugins {
                id 'com.palantir.baseline-java-versions'
            }

            tasks.withType(JavaCompile).configureEach({
                options.compilerArgs += ['-Werror']
                // See comment about fork options in BaselineImmutables
                options.fork = true
            })

            javaVersions {
                libraryTarget = %d
            }

            dependencies {
                annotationProcessor '%s'
                compileOnly '%s'
            }
            """, javaVersion, IMMUTABLES, IMMUTABLES_ANNOTATIONS);

        project.mainSourceSet().java().writeClass("""
            package com.palantir.one;
            import com.palantir.two.ImmutableTwo;
            import org.immutables.value.Value;
            @Value.Immutable
            public interface One {
                ImmutableTwo two();
            }
            """);

        project.mainSourceSet().java().writeClass("""
            package com.palantir.two;
            import org.immutables.value.Value;
            @Value.Immutable
            public interface Two {
                String value();
            }
            """);

        InvocationResult result = gradle.withArgs("compileJava").buildsSuccessfully();

        assertThat(result).task(":compileJava").succeeded();
    }

    @Test
    void handles_an_annotation_processor_source_set_extending_from_another_one(
            GradleInvoker gradle, RootProject project) {
        project.buildGradle().append("""
            dependencies {
                annotationProcessor '%s'
                compileOnly '%s'
            }

            configurations {
                testAnnotationProcessor.extendsFrom annotationProcessor
                testCompileOnly.extendsFrom compileOnly
            }
            """, IMMUTABLES, IMMUTABLES_ANNOTATIONS);

        project.testSourceSet().java().writeClass("""
            package test;
            import org.immutables.value.Value;
            @Value.Immutable
            public interface Test {
                int item();
            }
            """);

        InvocationResult result = gradle.withArgs("compileTestJava").buildsSuccessfully();

        assertThat(result).output().contains("compileTestJava: [-Aimmutables.gradle.incremental]");
    }
}
