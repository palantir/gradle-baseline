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


import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import spock.lang.Unroll

class BaselineJavaVersionIntegrationTest extends IntegrationSpec {
    private static final int JAVA_8_BYTECODE = 52
    private static final int JAVA_11_BYTECODE = 55
    private static final int JAVA_17_BYTECODE = 61

    def standardBuildFile = '''
        buildscript {
            repositories { mavenCentral() }
            dependencies {
                classpath 'com.netflix.nebula:nebula-publishing-plugin:17.0.0'
                classpath 'com.palantir.gradle.shadow-jar:gradle-shadow-jar:2.5.0'
                classpath 'com.palantir.gradle.consistentversions:gradle-consistent-versions:2.8.0'
            }
        }
        plugins {
            id 'java-library'
            id 'application'
        }
        
        apply plugin: 'com.palantir.baseline-java-versions'
        
        repositories {
            mavenCentral()
        }
        
        application {
            mainClass = 'Main'
        }
    '''.stripIndent(true)

    def java8CompatibleCode = '''
        public class Main { 
            public static void main(String[] args) {
                System.out.println("jdk8 features on runtime " + System.getProperty("java.specification.version"));
            }
        }
        '''.stripIndent(true)

    def java11CompatibleCode = '''
        import java.util.Optional;
        
        public class Main { 
            public static void main(String[] args) {
                Optional.of(args).isEmpty();
                System.out.println("jdk11 features on runtime " + System.getProperty("java.specification.version"));
            }
        }
        '''.stripIndent(true)

    def setup() {
        setFork(true)
        buildFile << standardBuildFile
    }

    def 'java 11 compilation fails targeting java 8'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 8
            runtime = 11
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode

        then:
        runTasksWithFailure('compileJava')
    }

    def 'distribution target is used when no artifacts are published'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            distributionTarget = 17
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        getBytecodeVersion(compiledClass) == JAVA_17_BYTECODE
    }

    def 'library target is used when no artifacts are published but project is overridden as a library'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            distributionTarget = 17
        }
        javaVersion {
            library()
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        getBytecodeVersion(compiledClass) == JAVA_11_BYTECODE
    }

    def 'library target is used when nebula maven publishing plugin is applied'() {
        when:
        buildFile << '''
        apply plugin: 'nebula.maven-publish'
        javaVersions {
            libraryTarget = 11
            distributionTarget = 17
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        getBytecodeVersion(compiledClass) == JAVA_11_BYTECODE
    }

    def 'library target is used when the palantir shadowjar plugin is applied'() {
        when:
        buildFile << '''
        apply plugin: 'com.palantir.consistent-versions' // required by shadow-jar
        apply plugin: 'com.palantir.shadow-jar'
        javaVersions {
            libraryTarget = 11
            distributionTarget = 17
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('--write-locks')
        runTasksSuccessfully('compileJava')
        getBytecodeVersion(compiledClass) == JAVA_11_BYTECODE
    }

    def 'java 11 compilation succeeds targeting java 11'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        getBytecodeVersion(compiledClass) == JAVA_11_BYTECODE
    }

    def 'java 11 execution succeeds on java 11'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        result.standardOutput.contains 'jdk11 features on runtime 11'
        getBytecodeVersion(compiledClass) == JAVA_11_BYTECODE
    }

    def 'java 11 execution succeeds on java 17'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            runtime = 17
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        result.standardOutput.contains 'jdk11 features on runtime 17'
        getBytecodeVersion(compiledClass) == JAVA_11_BYTECODE
    }

    def 'java 8 execution succeeds on java 8'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 8
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java8CompatibleCode

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        result.standardOutput.contains 'jdk8 features on runtime 1.8'
    }

    def 'java 8 execution succeeds on java 11'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 8
            runtime = 11
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java8CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        result.standardOutput.contains 'jdk8 features on runtime 11'
        getBytecodeVersion(compiledClass) == JAVA_8_BYTECODE
    }

    def 'JavaPluginConvention.getTargetCompatibility() produces the runtime java version'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            runtime = 17
        }
        task printTargetCompatibility() {
            doLast {
                System.out.println("[[[" + project.getConvention()
                .getPlugin(org.gradle.api.plugins.JavaPluginConvention.class)
                .getTargetCompatibility() + "]]]")
            }
        }
        '''.stripIndent(true)

        then:
        ExecutionResult result = runTasksSuccessfully('printTargetCompatibility')
        result.standardOutput.contains '[[[17]]]'
    }

    def 'verification should fail when target exceeds the runtime version'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 17
            runtime = 11
        }
        '''.stripIndent(true)

        then:
        ExecutionResult result = runTasksWithFailure('checkJavaVersions')
        result.standardError.contains 'The requested compilation target'
    }

    def 'verification should succeed when target and runtime versions match'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 17
            runtime = 17
        }
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('checkJavaVersions')
    }

    private static final int BYTECODE_IDENTIFIER = (int) 0xCAFEBABE

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    private static int getBytecodeVersion(File file) {
        try (InputStream stream = new FileInputStream(file)
             DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt()
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode")
            }
            // skip the minor version
            dis.readShort()
            return 0xFFFF & dis.readShort()
        }
    }
}
