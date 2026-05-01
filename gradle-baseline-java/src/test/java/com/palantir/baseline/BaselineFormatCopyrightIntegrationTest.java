/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@GradlePluginTests
@DisabledConfigurationCache
@DisabledForJreRange(minVersion = 14)
public class BaselineFormatCopyrightIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) throws IOException {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                rootProject.directory(".baseline").path().toFile());

        // Testing that an empty line is also OK, these can cause gotchas
        FileUtils.deleteDirectory(
                rootProject.directory(".baseline/copyright").path().toFile());
        rootProject.file(".baseline/copyright/050-test").overwrite("""

            (c) Copyright ${today.year} GoodCorp

                http://url-to-some-license
            """);
        rootProject.file(".baseline/copyright/000-also-works").overwrite("""

            (c) Copyright ${today.year} OtherCorp
            """);
    }

    /** The copyright that we expect will be generated when there isn't an existing one */
    private static final String generatedCopyright =
            """
            /*
             * (c) Copyright %d GoodCorp
             *
             *     http://url-to-some-license
             */
            """.formatted(LocalDate.now().getYear());

    private static final String generatedCopyright2015 = """
        /*
         * (c) Copyright 2015 GoodCorp
         *
         *     http://url-to-some-license
         */
        """;

    private static final String goodCopyright = """
        /*
         * (c) Copyright 2019 GoodCorp
         *
         *     http://url-to-some-license
         */
        """;

    private static final String goodCopyrightRange = """
        /*
         * (c) Copyright 2015-2019 GoodCorp
         *
         *     http://url-to-some-license
         */
        """;

    private static final String goodOtherCopyright = """
        /*
         * (c) Copyright 2019 OtherCorp
         */
        """;

    private static final String badCopyright = """
        /*
         * (c) Copyright 2015 EvilCorp
         *
         *     http://url-to-some-license
         */
        """;

    private GradleFile standardBuildFile(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("groovy").add("com.palantir.baseline-format");

        return rootProject.buildGradle().append("""
            repositories {
                // to resolve the `palantirJavaFormat` configuration
                mavenCentral()
            }
            dependencies {
                implementation localGroovy()
            }
            """);
    }

    private static final String validJavaFile = """

        package test;

        public class Test {}
        """;

    @ParameterizedTest(name = "check fails on {0} copyright in {3} project")
    @MethodSource("checkFailsArguments")
    void check_fails_on_copyright_type_copyright_in_lang_project(
            String copyrightType,
            String input,
            String expected,
            String lang,
            GradleInvoker gradle,
            RootProject rootProject) {
        standardBuildFile(rootProject);
        String javaFilePath = "src/main/" + lang + "/test/Test." + lang;
        rootProject.file(javaFilePath).overwrite(input).append(validJavaFile);

        InvocationResult fail = gradle.withArgs("check").buildsWithFailure();
        String spotlessTask = ":spotless" + Character.toUpperCase(lang.charAt(0)) + lang.substring(1) + "Check";
        assertThat(fail).task(spotlessTask).failed();
        assertThat(fail).output().contains("The following files had format violations");

        gradle.withArgs("format").buildsSuccessfully();

        rootProject
                .file(javaFilePath)
                .assertThat()
                .as("formatted file contains expected copyright for %s in %s", copyrightType, lang)
                .content()
                .contains(expected);
    }

    static Stream<Arguments> checkFailsArguments() {
        return Stream.of(
                Arguments.of("bad", badCopyright, generatedCopyright2015, "java"),
                Arguments.of("bad", badCopyright, generatedCopyright2015, "groovy"),
                Arguments.of("missing", "", generatedCopyright, "java"),
                Arguments.of("missing", "", generatedCopyright, "groovy"));
    }

    @ParameterizedTest(name = "check passes on correct {0} copyright in {2} project")
    @MethodSource("checkPassesArguments")
    void check_passes_on_correct_copyright_type_copyright_in_lang_project(
            String copyrightType, String copyright, String lang, GradleInvoker gradle, RootProject rootProject) {
        standardBuildFile(rootProject);
        String javaFilePath = "src/main/" + lang + "/test/Test." + lang;
        rootProject.file(javaFilePath).overwrite(copyright).append(validJavaFile);

        gradle.withArgs("check").buildsSuccessfully();
    }

    static Stream<Arguments> checkPassesArguments() {
        return Stream.of(
                Arguments.of("single year", goodCopyright, "java"),
                Arguments.of("year range", goodCopyrightRange, "java"),
                Arguments.of("single year other", goodOtherCopyright, "java"),
                Arguments.of("single year", goodCopyright, "groovy"),
                Arguments.of("year range", goodCopyrightRange, "groovy"),
                Arguments.of("single year other", goodOtherCopyright, "groovy"));
    }
}
