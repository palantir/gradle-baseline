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

import java.nio.file.Files
import java.util.stream.Stream
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class BaselineClassUniquenessPluginIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = """
        plugins {
            id 'java'
            id 'com.palantir.baseline-class-uniqueness'
        }
        subprojects {
            apply plugin: 'java'
        }
        repositories {
            mavenCentral()
        }
    """.stripIndent()

    def 'Task should run as part of :check'() {
        when:
        buildFile << standardBuildFile

        then:
        def result = with('check', '--stacktrace').build()
        result.task(':checkClassUniqueness').outcome == TaskOutcome.SUCCESS
    }

    def 'detect duplicates in two external jars'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            compile group: 'javax.el', name: 'javax.el-api', version: '3.0.0'
            compile group: 'javax.servlet.jsp', name: 'jsp-api', version: '2.1'
        }   
        """.stripIndent()
        BuildResult result = with('checkClassUniqueness').buildAndFail()

        then:
        result.getOutput().contains("Identically named classes found in 2 jars ([javax.servlet.jsp:jsp-api:2.1, javax.el:javax.el-api:3.0.0]): [javax.")
        result.getOutput().contains("'testRuntime' contains multiple copies of identically named classes")
    }

    def 'task should be up-to-date when classpath is unchanged'() {
        when:
        buildFile << standardBuildFile

        then:
        BuildResult result1 = with('checkClassUniqueness').build()
        result1.task(':checkClassUniqueness').outcome == TaskOutcome.SUCCESS

        BuildResult result = with('checkClassUniqueness').build()
        result.task(':checkClassUniqueness').outcome == TaskOutcome.UP_TO_DATE
    }

    def 'passes when no duplicates are present'() {
        when:
        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            compile 'com.google.guava:guava:19.0'
            compile 'org.apache.commons:commons-io:1.3.2'
            compile 'junit:junit:4.12'
            compile 'com.netflix.nebula:nebula-test:6.4.2'
        }
        """.stripIndent()
        BuildResult result = with('checkClassUniqueness', '--info').build()

        then:
        result.task(":checkClassUniqueness").outcome == TaskOutcome.SUCCESS
        println result.getOutput()
    }

    def 'should detect duplicates from transitive dependencies'() {
        when:
        multiProject.addSubproject('foo', """
        dependencies {
            compile group: 'javax.el', name: 'javax.el-api', version: '3.0.0'
        } 
        """)
        multiProject.addSubproject('bar', """
        dependencies {
            compile group: 'javax.servlet.jsp', name: 'jsp-api', version: '2.1'
        } 
        """)

        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            compile project(':foo')
            compile project(':bar')
        }
        """.stripIndent()

        then:
        BuildResult result = with('checkClassUniqueness').buildAndFail()
        result.output.contains("Identically named classes found in 2 jars")
    }

    def 'currently skips duplicates from user-authored code'() {
        when:
        Stream.of(multiProject.addSubproject('foo'), multiProject.addSubproject('bar')).forEach({ subproject ->
            File myClass = new File(subproject, "src/main/com/something/MyClass.java")
            Files.createDirectories(myClass.toPath().getParent())
            myClass << "package com.something; class MyClass {}"
        })

        buildFile << standardBuildFile
        buildFile << """
        dependencies {
            compile project(':foo')
            compile project(':bar')
        }
        """.stripIndent()

        then:
        BuildResult result = with('checkClassUniqueness', '--info').build()
        println result.getOutput()
        result.task(":checkClassUniqueness").outcome == TaskOutcome.SUCCESS // ideally should should say failed!
    }
}
