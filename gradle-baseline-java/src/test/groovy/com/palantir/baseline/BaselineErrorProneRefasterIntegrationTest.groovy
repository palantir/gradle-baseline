/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

/**
 * This test depends on ./gradlew :baseline-error-prone:publishToMavenLocal
 */
class BaselineErrorProneRefasterIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-error-prone'
            id 'org.inferred.processors' version '1.3.0'
        }
        repositories {
            mavenLocal()
            jcenter()
        }
        tasks.withType(JavaCompile) {
            options.compilerArgs += ['-Werror', '-Xlint:deprecation']
        }
    '''.stripIndent()

    def 'compileJava with refaster fixes CollectionsIsEmpty'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << '''
        package test;
        import java.util.ArrayList;
        public class Test {
            boolean empty = new ArrayList<>().size() == 0;
        }
        '''.stripIndent()

        then:
        BuildResult result = with('compileJava', '-i', '-PrefasterApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import java.util.ArrayList;
        public class Test {
            boolean empty = new ArrayList<>().isEmpty();
        }
        '''.stripIndent()
    }

    def 'compileJava with refaster fixes SortedFirst'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << '''
        package test;
        import java.util.*;
        import java.util.stream.Stream; 
        public class Test {
            Optional<Integer> i = Arrays.asList(5, -10, 7, -18, 23).stream()
                .sorted(Comparator.reverseOrder())
                .findFirst();
        }
        '''.stripIndent()

        then:
        BuildResult result = with('compileJava', '-i', '-PrefasterApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import java.util.*;
        import java.util.stream.Stream; 
        public class Test {
            Optional<Integer> i = Arrays.asList(5, -10, 7, -18, 23).stream().min(Comparator.reverseOrder());
        }
        '''.stripIndent()
    }

    def 'compileJava with refaster fixes Utf8Length'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << '''
        package test;
        import java.nio.charset.StandardCharsets;
        public class Test {
            int i = "hello world".getBytes(StandardCharsets.UTF_8).length;
        }
        '''.stripIndent()

        then:
        BuildResult result = with('compileJava', '-i', '-PrefasterApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import com.google.common.base.Utf8;
        import java.nio.charset.StandardCharsets;
        public class Test {
            int i = Utf8.encodedLength("hello world");
        }
        '''.stripIndent()
    }

    def 'compileJava with refaster fixes Utf8Length with deprecated method'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << '''
        package test;
        import com.google.common.base.CharMatcher;
        import java.nio.charset.StandardCharsets;
        public class Test {
            CharMatcher matcher = CharMatcher.digit();  // Would normally fail with -Xlint:deprecation
            int i = "hello world".getBytes(StandardCharsets.UTF_8).length;
        }
        '''.stripIndent()

        then:
        BuildResult result = with('compileJava', '-i', '-PrefasterApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import com.google.common.base.CharMatcher;
        import com.google.common.base.Utf8;
        import java.nio.charset.StandardCharsets;
        public class Test {
            CharMatcher matcher = CharMatcher.digit();  // Would normally fail with -Xlint:deprecation
            int i = Utf8.encodedLength("hello world");
        }
        '''.stripIndent()
    }

    def 'refaster configuration can be overridden'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
        dependencies {
            // this isn't actually a refaster jar, just want to make sure the baseline ones don't run!
            refaster 'org.codehaus.cargo:empty-jar:1.7.7'
        }
        '''
        file('src/main/java/test/Test.java') << '''
        package test;
        import java.nio.charset.StandardCharsets;
        public class Test {
            int i = "hello world".getBytes(StandardCharsets.UTF_8).length;
        }
        '''.stripIndent()

        then:
        BuildResult result = with('compileJava', '-i', '-PrefasterApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        // this signifies that the baked-in baseline refaster ruels were *not* applied
        file('src/main/java/test/Test.java').text.contains 'int i = "hello world".getBytes(StandardCharsets.UTF_8).length;'
    }
}
