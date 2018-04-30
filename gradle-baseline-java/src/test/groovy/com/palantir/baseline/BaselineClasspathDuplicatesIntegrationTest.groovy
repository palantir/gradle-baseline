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

class BaselineClasspathDuplicatesIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = """
        plugins {
            id 'java'
            id 'com.palantir.baseline-classpath-duplicates'
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
        result.task(':checkUniqueClassNames').outcome == TaskOutcome.SUCCESS
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
        BuildResult result = with('checkUniqueClassNames').buildAndFail()

        then:
        result.getOutput().contains("Identically named classes found in 2 jars ([javax.servlet.jsp:jsp-api:2.1, javax.el:javax.el-api:3.0.0]): [javax.")
        result.getOutput().contains("'testRuntime' contains multiple copies of identically named classes")
    }

    def 'task should be up-to-date when classpath is unchanged'() {
        when:
        buildFile << standardBuildFile

        then:
        BuildResult result1 = with('checkUniqueClassNames').build()
        result1.task(':checkUniqueClassNames').outcome == TaskOutcome.SUCCESS

        BuildResult result = with('checkUniqueClassNames').build()
        result.task(':checkUniqueClassNames').outcome == TaskOutcome.UP_TO_DATE
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
        BuildResult result = with('checkUniqueClassNames', '--info').build()

        then:
        result.task(":checkUniqueClassNames").outcome == TaskOutcome.SUCCESS
        println result.getOutput()
    }
}
