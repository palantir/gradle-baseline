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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineFormatIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-format'
        }
    '''.stripIndent()

    def noJavaBuildFile = '''
        plugins {
            id 'com.palantir.baseline-format'
        }
    '''.stripIndent()

    def validJavaFile = '''
    package test;
    import com.java.tests;
    public class Test { void test() {} }
    '''.stripIndent()

    def 'can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        with('format', '--stacktrace').build()
    }

    def 'cannot run format task when java plugin is missing'() {
        when:
        buildFile << noJavaBuildFile

        then:
        with('format', '--stacktrace').buildAndFail()
    }

    def 'format task fixes styles'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << validJavaFile

        then:
        BuildResult result = with('format').build();
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
            package test;
            public class Test { void test() {} }
        '''.stripIndent()
    }
//
//    def 'format task works on new source sets'() {
//        when:
//        buildFile << standardBuildFile
//        buildFile << '''
//            sourceSets { foo }
//        '''.stripIndent()
//        file('src/foo/java/test/Test.java') << validJavaFile
//
//        then:
//        BuildResult result = with('format').build()
//        result.task(":format").outcome == TaskOutcome.SUCCESS
//        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
//        file('src/foo/java/test/Test.java').text == '''
//            package test;
//            public class Test { void test() {} }
//        '''.stripIndent()
//    }
//
//    def 'format task works on other language java sources'() {
//        when:
//        buildFile << standardBuildFile
//        buildFile << '''
//            apply plugin: 'groovy'
//            sourceSets { foo }
//        '''.stripIndent()
//        file('src/foo/groovy/test/Test.java') << validJavaFile
//
//        then:
//        BuildResult result = with('format').build()
//        result.task(":format").outcome == TaskOutcome.SUCCESS
//        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
//        file('src/foo/groovy/test/Test.java').text == '''
//            package test;
//            public class Test { void test() {} }
//        '''.stripIndent()
//    }

    def 'format ignores generated files'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            sourceSets { generated }
        '''.stripIndent()
        file('src/generated/java/test/Test.java') << '''
            package test;
            import java.lang.Void;
            public class Test { Void test() {} }
        '''.stripIndent()

        then:
        BuildResult result = with('spotlessJavaCheck').build();
        result.task(":spotlessJava").outcome == TaskOutcome.SUCCESS
    }
}
