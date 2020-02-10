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

package com.palantir.baseline

import java.time.LocalDate
import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

@Unroll
class BaselineFormatCopyrightIntegrationTest extends AbstractPluginTest {

    def setup() {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(projectDir, ".baseline"))

        // Testing that an empty line is also OK, these can cause gotchas
        file(".baseline/copyright").deleteDir()
        file(".baseline/copyright/050-test") << '''
            (c) Copyright $YEAR GoodCorp
            
            EXTRA
        '''.stripIndent()
        file(".baseline/copyright/000-also-works") << '''
            (c) Copyright $YEAR OtherCorp
        '''.stripIndent()
    }

    /** The copyright that we expect will be generated when there isn't an existing one */
    static generatedCopyright = """\
        /*
         * (c) Copyright ${LocalDate.now().year} GoodCorp
         *
         * EXTRA
         */
    """.stripIndent()

    static generatedCopyright2015 = """\
        /*
         * (c) Copyright 2015 GoodCorp
         *
         * EXTRA
         */
    """.stripIndent()

    static goodCopyright = """\
        /*
         * (c) Copyright 2019 GoodCorp
         *
         * EXTRA
         */
    """.stripIndent()

    static goodCopyrightRange = """\
        /*
         * (c) Copyright 2015-2019 GoodCorp
         *
         * EXTRA
         */
    """.stripIndent()

    static goodOtherCopyright = """\
        /*
         * (c) Copyright 2019 OtherCorp
         */
    """.stripIndent()

    static badCopyright = """\
        /*
         * (c) Copyright 2015 EvilCorp
         *
         * EXTRA
         */
    """.stripIndent()

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'groovy'
            id 'com.palantir.baseline-format'
        }
        repositories {
            // to resolve the `palantirJavaFormat` configuration
            maven { url 'https://dl.bintray.com/palantir/releases' }
            jcenter()
        }
        dependencies {
            implementation localGroovy()
        }
    '''.stripIndent()

    def validJavaFile = '''
    package test;
    
    public class Test {}
    '''.stripIndent()

    def 'check fails on #copyrightType copyright in #lang project'() {
        buildFile << standardBuildFile
        def javaFile = file("src/main/$lang/test/Test.$lang")
        javaFile << input
        javaFile << validJavaFile

        expect:
        def fail = with('check').buildAndFail()
        fail.task(":spotless${lang.capitalize()}").outcome == TaskOutcome.FAILED
        fail.output.contains("The following files had format violations")

        when:
        with('format').build()

        then:
        javaFile.text.startsWith(expected)

        where:
        copyrightType | input        | expected               | lang
        "bad"         | badCopyright | generatedCopyright2015 | "java"
        "bad"         | badCopyright | generatedCopyright2015 | "groovy"
        "missing"     | ''           | generatedCopyright     | "java"
        "missing"     | ''           | generatedCopyright     | "groovy"
    }

    def 'check passes on correct #copyrightType copyright in #lang project'() {
        buildFile << standardBuildFile
        def javaFile = file("src/main/$lang/test/Test.$lang")
        javaFile << copyright
        javaFile << validJavaFile

        expect:
        with('check').build()

        where:
        copyrightType       | copyright          | lang
        "single year"       | goodCopyright      | "java"
        "year range"        | goodCopyrightRange | "java"
        "single year other" | goodOtherCopyright | "java"
        "single year"       | goodCopyright      | "groovy"
        "year range"        | goodCopyrightRange | "groovy"
        "single year other" | goodOtherCopyright | "groovy"
    }
}
