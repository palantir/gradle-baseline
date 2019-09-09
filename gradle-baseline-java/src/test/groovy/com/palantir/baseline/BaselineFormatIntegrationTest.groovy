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

import com.google.common.io.Resources
import java.nio.charset.Charset
import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineFormatIntegrationTest extends AbstractPluginTest {

    def setup() {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(projectDir, ".baseline"))
    }

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

    public class Test {
        void test() {
            int x = 1;
            System.out.println(
                    "Hello");
            Optional.of("hello").orElseGet(() -> {
                return "Hello World";
            });
        }
    }
    '''.stripIndent()

    def invalidJavaFile = '''
    package test;
    import com.java.unused;
    public class Test { void test() {int x = 1;
        System.out.println(
            "Hello"
        );
        Optional.of("hello").orElseGet(() -> { 
            return "Hello World";
        });
    } }
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
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('format', '-Pcom.palantir.baseline-format.eclipse').build();
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == validJavaFile
    }

    def 'format task works on new source sets'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            sourceSets { foo }
        '''.stripIndent()
        file('src/foo/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('format', '-Pcom.palantir.baseline-format.eclipse').build()
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
        file('src/foo/java/test/Test.java').text == validJavaFile
    }

    def 'format task works on other language java sources'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            apply plugin: 'groovy'
            sourceSets { foo }
        '''.stripIndent()
        file('src/foo/groovy/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('format', '-Pcom.palantir.baseline-format.eclipse').build()
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
        file('src/foo/groovy/test/Test.java').text == validJavaFile
    }

    def 'format ignores generated files'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            sourceSets {
                main {
                    java { srcDir 'src/generated/java' }
                }
            }
            
            // ensure file is in the source set
            sourceSets.main.allJava.filter { it.name == "Test.java" }.singleFile
        '''.stripIndent()
        def javaFileContents = '''
            package test;
            import java.lang.Void;
            public class Test { Void test() {} }
        '''.stripIndent()
        file('src/generated/java/test/Test.java') << javaFileContents

        then:
        BuildResult result = with('spotlessJavaCheck').build()
        result.task(":spotlessJava").outcome == TaskOutcome.SUCCESS
    }

    def 'format ignores blank lines in block or javadoc comment'() {
        when:
        buildFile << standardBuildFile
        def javaFileContents = Resources.toString(Resources.getResource(this.class, "blank-lines-in-comments.java"), Charset.defaultCharset())
        file('src/main/java/test/Test.java').text = javaFileContents

        then:
        BuildResult result = with('spotlessJavaCheck').build()
        result.task(":spotlessJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == javaFileContents
    }

}
