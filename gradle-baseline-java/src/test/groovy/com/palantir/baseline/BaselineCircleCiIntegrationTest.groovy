/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

class BaselineCircleCiIntegrationTest extends AbstractPluginTest {
    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-circleci'
        }
        
        repositories {
            jcenter()
        }
        
        dependencies {
            testCompile 'junit:junit:4.12'
        }
    '''.stripIndent()

    def javaFile = '''
        package test;
        
        import org.junit.Test;
        
        public class TestClass { 
            @Test
            public void test() {} 
        }
        '''.stripIndent()

    def setup() {
        new File(System.getenv('CIRCLE_ARTIFACTS')).toPath().deleteDir()
    }

    def 'applies the configuration resolver plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        BuildResult result = with('resolveConfigurations').build()
        result.task(':resolveConfigurations').outcome == TaskOutcome.SUCCESS
    }

    def 'collects html reports'() {
        when:
        buildFile << standardBuildFile
        file('src/test/java/test/TestClass.java') << javaFile

        String artifacts = System.getenv('CIRCLE_ARTIFACTS')
        then:
        BuildResult result = with('test').build()
        result.task(':test').outcome == TaskOutcome.SUCCESS
        new File(new File(artifacts, 'junit'), 'test').list().toList().toSet() == ['classes', 'css', 'index.html', 'js', 'packages'].toSet()
    }

    def 'collects build profiles'() {
        when:
        buildFile << standardBuildFile

        String artifacts = System.getenv('CIRCLE_ARTIFACTS')
        Set<String> defaultDirs = ['js', 'css'].toSet()
        then:
        BuildResult result = with('build', '--profile').build()
        result.task(':build').outcome == TaskOutcome.SUCCESS
        Set<String> files = new File(artifacts, 'profile').list().toList().toSet()
        files.size() == 3
        files.containsAll(defaultDirs)
        files.removeAll(defaultDirs)
        String profileFile = files.iterator().next()
        profileFile.startsWith("profile-")
        profileFile.endsWith(".html")
    }
}
