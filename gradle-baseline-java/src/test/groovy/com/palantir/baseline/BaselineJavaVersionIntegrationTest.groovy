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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.assertj.core.api.Assumptions

/**
 * This test exercises both the root-plugin {@code BaselineJavaVersions} AND the subproject
 * specific plugin, {@code BaselineJavaVersion}.
 */
class BaselineJavaVersionIntegrationTest extends IntegrationSpec {
    private static final int JAVA_8_BYTECODE = 52
    private static final int JAVA_11_BYTECODE = 55
    private static final int JAVA_17_BYTECODE = 61
    private static final int ENABLE_PREVIEW_BYTECODE = 65535
    private static final int NOT_ENABLE_PREVIEW_BYTECODE = 0

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

    def java17PreviewCode = '''
        public class Main {
            sealed interface MyUnion {
                record Foo(int number) implements MyUnion {}
            }
        
            public static void main(String[] args) {
                MyUnion myUnion = new MyUnion.Foo(1234);
                switch (myUnion) {
                    case MyUnion.Foo foo -> System.out.println("Java 17 pattern matching switch: " + foo.number);
                }
            }
        }
        '''

    def setup() {
        // Fork needed or build fails on circleci with "SystemInfo is not supported on this operating system."
        // Comment out locally in order to get debugging to work
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
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
    }

    def 'java 17 preview compilation works'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            distributionTarget = '17_PREVIEW'
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java17PreviewCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava', '-i')
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, ENABLE_PREVIEW_BYTECODE)
    }

    def 'java 17 preview javadoc works'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            distributionTarget = '17_PREVIEW'
        }
        '''.stripIndent(true)
        file('src/main/java/Main.java') << java17PreviewCode

        then:
        runTasksSuccessfully('javadoc', '-i')
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
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
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
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
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
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
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
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
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
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
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
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
    }

    def 'java 8 execution succeeds on java 8'() {
        Assumptions.assumeThat(System.getProperty("os.arch")).describedAs(
                "On an M1 mac, this test will fail to download https://api.adoptopenjdk.net/v3/binary/latest/8/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk")
                .isNotEqualTo("aarch64");
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
        Assumptions.assumeThat(System.getProperty("os.arch")).describedAs(
                "On an M1 mac, this test will fail to download https://api.adoptopenjdk.net/v3/binary/latest/8/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk")
                .isNotEqualTo("aarch64");

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
        assertBytecodeVersion(compiledClass, JAVA_8_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)
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

    def 'verification should fail when --enable-preview is on, but versions differ'() {
        when:
        buildFile << '''
        javaVersions {
            distributionTarget = '11_PREVIEW'
            runtime = '15_PREVIEW'
        }
        '''.stripIndent(true)

        then:
        ExecutionResult result = runTasksWithFailure('checkJavaVersions')
        result.standardError.contains 'Runtime Java version (15_PREVIEW) must be exactly the same as the compilation target (11_PREVIEW)'
    }

    def 'verification should fail when runtime does not use --enable-preview but compilation does'() {
        when:
        buildFile << '''
        javaVersions {
            distributionTarget = '17_PREVIEW'
            runtime = '17'
        }
        '''.stripIndent(true)

        then:
        ExecutionResult result = runTasksWithFailure('checkJavaVersions')
        result.standardError.contains 'Runtime Java version (17) must be exactly the same as the compilation target (17_PREVIEW)'
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

    def 'can configure a jdk path to be used'() {
        Assumptions.assumeThat(System.getenv("CI")).describedAs(
                "This test deletes a directory locally, you don't want to run it on your mac").isNotNull();

        Path newJavaHome = Files.createSymbolicLink(
                projectDir.toPath().resolve("jdk"),
                Paths.get(System.getProperty("java.home")))

        // language=gradle
        buildFile << """
            javaVersions {
                libraryTarget = 11
                
                jdk JavaLanguageVersion.of(11), new JavaInstallationMetadata() {   
                    @Override
                    JavaLanguageVersion getLanguageVersion() {
                        return JavaLanguageVersion.of(11)
                    }
                    @Override
                    String getJavaRuntimeVersion() {
                        return '11.0.222'
                    }
                    @Override
                    String getJvmVersion() {
                        return '11.33.44'
                    }
                    @Override
                    String getVendor() {
                        return 'vendor'
                    }
                    @Override
                    Directory getInstallationPath() {
                        return layout.dir(provider { new File('$newJavaHome') }).get()
                    }
                }
            }
        """.stripIndent(true)

        writeJavaSourceFile java11CompatibleCode

        when:
        def stdout = runTasksSuccessfully('compileJava', '--stacktrace').standardOutput

        then:
        stdout.contains(newJavaHome.toString())
    }

    private static final int BYTECODE_IDENTIFIER = (int) 0xCAFEBABE

    // See http://illegalargumentexception.blogspot.com/2009/07/java-finding-class-versions.html
    private static void assertBytecodeVersion(File file, int expectedMajorBytecodeVersion,
                                              int expectedMinorBytecodeVersion) {
        try (InputStream stream = new FileInputStream(file)
             DataInputStream dis = new DataInputStream(stream)) {
            int magic = dis.readInt()
            if (magic != BYTECODE_IDENTIFIER) {
                throw new IllegalArgumentException("File " + file + " does not appear to be java bytecode")
            }
            int minorBytecodeVersion = 0xFFFF & dis.readShort()
            int majorBytecodeVersion = 0xFFFF & dis.readShort()

            majorBytecodeVersion == expectedMajorBytecodeVersion
            minorBytecodeVersion == expectedMinorBytecodeVersion
        }
    }
}
