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

package com.palantir.baseline


import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

class BaselineNullAwayIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-error-prone'
            id 'com.palantir.baseline-null-away'
        }
        repositories {
            mavenLocal()
            mavenCentral()
        }
        tasks.withType(JavaCompile).configureEach {
            options.compilerArgs += ['-Werror']
        }
    '''.stripIndent()

    def validJavaFile = '''
        package com.palantir.test;
        public class Test { void test() {} }
        '''.stripIndent()

    def invalidJavaFile = '''
        package com.palantir.test;
        public class Test {
            int test(Throwable throwable) {
                // uh-oh, getMessage may be null!
                return throwable.getMessage().hashCode();
            }
        }
        '''.stripIndent()

    def 'Can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        with('compileJava', '--info').build()
    }

    def 'compileJava fails when null-away finds errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/com/palantir/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[NullAway] dereferenced expression throwable.getMessage() is @Nullable")
    }

    def 'compileJava succeeds when null-away finds no errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/com/palantir/test/Test.java') << validJavaFile

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
    }
}
