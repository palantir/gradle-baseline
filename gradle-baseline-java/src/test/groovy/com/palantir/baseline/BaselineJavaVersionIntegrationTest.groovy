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

    def standardBuildFile = '''
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

    def 'java 11 compilation fails targeting java 8'() {
        when:
        buildFile << standardBuildFile
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

    def 'java 11 compilation succeeds targeting java 11'() {
        when:
        buildFile << standardBuildFile
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
        buildFile << standardBuildFile
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
        buildFile << standardBuildFile
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
        buildFile << standardBuildFile
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
        buildFile << standardBuildFile
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
        buildFile << standardBuildFile
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
