/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.Disabled
import spock.lang.Unroll
/**
 * This test depends on ./gradlew :baseline-error-prone:publishToMavenLocal
 */
class BaselineErrorProneIntegrationTest extends AbstractPluginTest {

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-error-prone'
        }
        repositories {
            mavenLocal()
            // TODO(forozco): figure out why pTML no longer works
            mavenCentral()
        }
    '''.stripIndent()

    def validJavaFile = '''
        package test;
        public class Test { void test() {} }
        '''.stripIndent()

    def invalidJavaFile = '''
        package test;
        import java.util.Optional;
        public class Test {
            void test() {
                int[] a = {1, 2, 3};
                int[] b = {1, 2, 3};
                if (a.equals(b)) {
                  System.out.println("arrays are equal!");
                  Optional.of("hello").orElse(System.getProperty("world"));
                }
            }
        }
        '''.stripIndent()

    def 'Can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        with('compileJava', '--info').build()
    }

    def 'compileJava fails when there is an unclosed stream of files'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            void test() throws java.io.IOException {
                java.nio.file.Files.list(java.nio.file.Paths.get("/")).collect(java.util.stream.Collectors.toList());
            }
        }
        '''

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[StreamResourceLeak] Streams that encapsulate a closeable resource should be closed using try-with-resources")
    }

    def 'compileJava fails when error-prone finds errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[ArrayEquals] Reference equality used to compare arrays")
    }

    def 'compileJava fails when using deprecated APIs'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            dependencies {
                // CheckedServiceException constructors are deprecated for removal in this version
                implementation 'com.palantir.conjure.java.api:errors:2.65.0'
            }
        '''.stripIndent(true)

        file('src/main/java/test/Test.java') << '''
            package test;
    
            import com.palantir.conjure.java.api.errors.CheckedServiceException;
            import com.palantir.conjure.java.api.errors.ErrorType;
    
            public class Test extends CheckedServiceException {
                public Test() {
                    super(ErrorType.CONFLICT);
                }
            }
        '''.stripIndent(true)

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("CheckedServiceException#<init> is deprecated for removal")
    }

    def 'compileJava succeeds when using deprecated-for-removal APIs, even with -Werror, if check is disabled'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            dependencies {
                // CheckedServiceException constructors are deprecated for removal in this version
                implementation 'com.palantir.conjure.java.api:errors:2.65.0'
            }
            tasks.withType(JavaCompile) {
                options.compilerArgs += ['-Werror']
                options.errorprone {
                    check 'DeprecatedForRemovalApiUsage', net.ltgt.gradle.errorprone.CheckSeverity.OFF
                }
            }
        '''.stripIndent(true)

        file('src/main/java/test/Test.java') << '''
            package test;
    
            import com.palantir.conjure.java.api.errors.CheckedServiceException;
            import com.palantir.conjure.java.api.errors.ErrorType;
    
            public class Test extends CheckedServiceException {
                public Test() {
                    super(ErrorType.CONFLICT);
                }
            }
        '''.stripIndent(true)

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
    }

    def 'compileJava succeeds when using deprecated if deprecated API is in the same project'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            tasks.withType(JavaCompile) {
                options.compilerArgs += ['-Werror']
            }
        '''.stripIndent(true)

        file('src/main/java/test/DeprecatedClass.java') << '''
            package test;
            @Deprecated(forRemoval = true)
            public class DeprecatedClass {
                @Deprecated(forRemoval = true)
                static void deprecated() {}
                
                // Testing nested classes too
                @Deprecated(forRemoval = true)
                public static class Inner {}
            }
        '''.stripIndent(true)

        file('src/main/java/test/Test.java') << '''
            package test;
            public class Test {
                // The object parameter is to ensure that we also notice classes
                //   marked as deprecated in the same project/repo
                void test(DeprecatedClass obj) {
                    obj.deprecated();
                }
                
                void testInner(DeprecatedClass.Inner _obj) {}
            }
        '''.stripIndent(true)

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
    }

    def 'compileJava succeeds when using deprecated if deprecated API is in the same repo, in different subprojects'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            tasks.withType(JavaCompile) {
                options.compilerArgs += ['-Werror']
            }
        '''.stripIndent(true)

        def standardBuildFileForLibrary = standardBuildFile.replace("'java'", "'java-library'")
        multiProject.addSubproject("lib", standardBuildFileForLibrary)
        multiProject.addSubproject("app", standardBuildFile + '''
            dependencies {
                implementation project(':lib')
            }
        '''.stripIndent(true))

        file('lib/src/main/java/test/DeprecatedClass.java') << '''
            package test;
            @Deprecated(forRemoval = true)
            public class DeprecatedClass {
                @Deprecated(forRemoval = true)
                static void deprecated() {}
                
                // Testing nested classes too
                @Deprecated(forRemoval = true)
                public static class Inner {}
            }
        '''.stripIndent(true)

        file('app/src/main/java/test/Test.java') << '''
            package test;
            public class Test {
                // The object parameter is to ensure that we also notice classes
                //   marked as deprecated in the same project/repo
                void test(DeprecatedClass obj) {
                    obj.deprecated();
                }
                
                void testInner(DeprecatedClass.Inner _obj) {}
            }
        '''.stripIndent(true)

        then:
        BuildResult result = with('compileJava').build()
        result.task(":lib:compileJava").outcome == TaskOutcome.SUCCESS
        result.task(":app:compileJava").outcome == TaskOutcome.SUCCESS
    }

    def 'compileJava fails when StrictUnusedVariable finds errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            void test() {
                int a = 5;
            }
        }
        '''.stripIndent()

        then:
        BuildResult result = with('compileJava').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[StrictUnusedVariable]")
    }

    def 'error-prone can be disabled using property'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava', '-Pcom.palantir.baseline-error-prone.disable').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
    }

    def 'error-prone is not disabled in IntelliJ'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava', '-Didea.active=true').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[ArrayEquals] Reference equality used to compare arrays")

    }

    def 'error-prone can be enabled using property'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava', '-Pcom.palantir.baseline-error-prone.disable=false', '-Didea.active=true').buildAndFail()
        result.task(":compileJava").outcome == TaskOutcome.FAILED
        result.output.contains("[ArrayEquals] Reference equality used to compare arrays")
    }

    def 'compileJava succeeds when error-prone finds no errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << validJavaFile

        then:
        BuildResult result = with('compileJava').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
    }

    def 'compileJava applies patches when error-prone finds errors'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava', '-PerrorProneApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import java.util.Arrays;
        import java.util.Optional;
        public class Test {
            void test() {
                int[] a = {1, 2, 3};
                int[] b = {1, 2, 3};
                if (Arrays.equals(a, b)) {
                  System.out.println("arrays are equal!");
                  Optional.of("hello").orElseGet(() -> System.getProperty("world"));
                }
            }
        }
        '''.stripIndent()
    }

    def 'compileJava by itself does not run annoying error-prones'() {
        given: 'A java file which violates StrictUnusedVariable'
        buildFile << standardBuildFile
        file('src/main/java/foo/Foo.java') << '''
            package foo;

            public class Foo {
                void foo() {
                    int x = 5;
                }
            }
        '''.stripIndent(true)

        when: 'Running compileJava by itself'
        BuildResult compileJavaResult = with('compileJava').build()

        then: 'StrictUnusedVariable does not fire'
        compileJavaResult.task(":compileJava").outcome == TaskOutcome.SUCCESS
        !compileJavaResult.output.contains('error: [StrictUnusedVariable]')

        when: 'Running compileJava via check'
        BuildResult checkResult = with('check').buildAndFail()

        then: 'StrictUnusedVariable fires'
        checkResult.task(":compileJava").outcome == TaskOutcome.FAILED
        checkResult.output.contains('error: [StrictUnusedVariable]')
    }

    def 'compileJava applies patches when errorProneApply contains specific checks'() {
        when:
        buildFile << standardBuildFile
        file('src/main/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('compileJava', '-PerrorProneApply=OptionalOrElseMethodInvocation').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == '''
        package test;
        import java.util.Optional;
        public class Test {
            void test() {
                int[] a = {1, 2, 3};
                int[] b = {1, 2, 3};
                if (a.equals(b)) {
                  System.out.println("arrays are equal!");
                  Optional.of("hello").orElseGet(() -> System.getProperty("world"));
                }
            }
        }
        '''.stripIndent()
    }

    enum CheckConfigurationMethod { ARG, DSL }

    @Unroll
    def 'compileJava does not apply patches for error-prone checks that were turned OFF via #checkConfigurationMethod'() {
        def checkName = "Slf4jLogsafeArgs"
        def turnOffCheck = [
                (CheckConfigurationMethod.ARG): "options.errorprone.disable '$checkName'",
                (CheckConfigurationMethod.DSL): """
                    options.errorprone {
                        check '$checkName', net.ltgt.gradle.errorprone.CheckSeverity.OFF
                    }
                """.stripIndent(),
        ]

        buildFile << standardBuildFile
        buildFile << """
            tasks.withType(JavaCompile) {
                ${turnOffCheck[checkConfigurationMethod]}
            }
            dependencies {
                implementation 'org.slf4j:slf4j-api:1.7.25'
            }
        """.stripIndent()

        def correctJavaFile = '''
        package test;
        import org.slf4j.LoggerFactory;
        import org.slf4j.Logger;
        public class Test {
            void test() {
                Logger log = LoggerFactory.getLogger("foo");
                log.info("Hi there {}", "non safe arg");
            }
        }
        '''.stripIndent()
        file('src/main/java/test/Test.java') << correctJavaFile

        expect:
        BuildResult result = with('compileJava', '-PerrorProneApply').build()
        result.task(":compileJava").outcome == TaskOutcome.SUCCESS
        file('src/main/java/test/Test.java').text == correctJavaFile

        where:
        checkConfigurationMethod << CheckConfigurationMethod.values()
    }
}
