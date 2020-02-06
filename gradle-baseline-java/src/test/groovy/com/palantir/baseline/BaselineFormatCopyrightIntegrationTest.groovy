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

        file(".baseline/copyright/000test") << '''
            (c) Copyright $YEAR Palantir
        '''.stripIndent()
    }

    /** The copyright that we expect will be generated when there isn't an existing one */
    static generatedCopyright = """\
        /*
         * (c) Copyright ${LocalDate.now().year} Palantir
         */
    """.stripIndent()

    static goodCopyright = """\
        /*
         * (c) Copyright 2019 Palantir
         */
    """.stripIndent()

    static goodCopyrightRange = """\
        /*
         * (c) Copyright 2015-2019 Palantir
         */
    """.stripIndent()

    static badCopyright = """\
        /*
         * (c) Copyright 2015 EvilCorp
         */
    """.stripIndent()

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-format'
        }
        repositories {
            // to resolve the `palantirJavaFormat` configuration
            maven { url 'https://dl.bintray.com/palantir/releases' }
            jcenter()
        }
    '''.stripIndent()

    def validJavaFile = '''
    package test;
    
    public class Test {}
    '''.stripIndent()

    def 'check fails on bad or missing copyright in java project'() {
        buildFile << standardBuildFile
        def javaFile = file('src/main/java/test/Test.java')
        javaFile << copyright
        javaFile << validJavaFile

        expect:
        def fail = with('check').buildAndFail()
        fail.task(":spotlessJava").outcome == TaskOutcome.FAILED
        fail.output.contains("The following files had format violations")

        when:
        with('format').build()

        then:
        javaFile.text.startsWith(generatedCopyright)

        where:
        copyright << [badCopyright, '']
    }

    def 'check passes on correct copyright in java project'() {
        buildFile << standardBuildFile
        def javaFile = file('src/main/java/test/Test.java')
        javaFile << copyright
        javaFile << validJavaFile

        expect:
        with('check').build()

        where:
        copyright << [goodCopyright, goodCopyrightRange]
    }
}
