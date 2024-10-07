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

import com.google.common.base.Throwables
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.assertj.core.api.Assumptions
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * This test exercises both the root-plugin {@code BaselineJavaVersions} AND the subproject
 * specific plugin, {@code BaselineJavaVersion}.
 */
@Unroll
class BaselineJavaVersionIntegrationTest extends IntegrationSpec {
    private static final List<String> GRADLE_TEST_VERSIONS = ['7.6.4', '8.8', GradleVersion.current().getVersion()]

    private static final int JAVA_8_BYTECODE = 52
    private static final int JAVA_11_BYTECODE = 55
    private static final int JAVA_17_BYTECODE = 61
    private static final int ENABLE_PREVIEW_BYTECODE = 65535
    private static final int NOT_ENABLE_PREVIEW_BYTECODE = 0

    File mainJava

    // language=Gradle
    def standardBuildFile = '''
        buildscript {
            repositories { mavenCentral() }
            dependencies {
                classpath 'com.palantir.sls-packaging:gradle-sls-packaging:7.56.0'
                classpath 'com.palantir.gradle.jdkslatest:gradle-jdks-latest:0.13.0'
            }
        }
        plugins {
            id 'java'
        }
        
        allprojects {
            repositories {
                mavenCentral()
            }
        }
        
        apply plugin: 'com.palantir.baseline-java-versions'
        
        task runMainClass(type: JavaExec) {
            mainClass = 'Main'
            classpath = sourceSets.main.runtimeClasspath
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

        mainJava = file("src/main/java/Main.java")
    }

    def '#gradleVersionNumber: java 11 compilation fails targeting java 8'() {
        when:
        gradleVersion = gradleVersionNumber
        buildFile << '''
        javaVersions {
            libraryTarget = 8
            runtime = 11
        }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode

        then:
        runTasksWithFailure('compileJava')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: distribution target is used when no artifacts are published'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            distributionTarget = 17
        }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 17 preview compilation works'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            distributionTarget = '17_PREVIEW'
        }
        '''.stripIndent(true)
        mainJava << java17PreviewCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava', '-i')
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: setting library target to preview version fails'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = '17_PREVIEW'
        }
        '''.stripIndent(true)
        mainJava << java17PreviewCode

        then:
        ExecutionResult result = runTasksWithFailure('compileJava', '-i')
        result.standardError.contains 'cannot be run on newer JVMs'

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 17 preview on single project works'() {
        when:
        buildFile << '''
        javaVersion {
            runtime = '17_PREVIEW'
            target = '17_PREVIEW'
        }
        '''.stripIndent(true)
        mainJava << java17PreviewCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava', '-i')
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 17 preview javadoc works'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            distributionTarget = '17_PREVIEW'
        }
        '''.stripIndent(true)
        mainJava << java17PreviewCode

        then:
        runTasksSuccessfully('javadoc', '-i')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: library target is used when no artifacts are published but project is overridden as a library'() {
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
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: distribution target is used when sls-packaging is used'() {
        when:
        // language=Gradle
        buildFile << '''
            apply plugin: 'com.palantir.sls-java-service-distribution'
            javaVersions {
                libraryTarget = 11
                distributionTarget = 17
            }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        assertBytecodeVersion(compiledClass, JAVA_17_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 11 compilation succeeds targeting java 11'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = '11'
        }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksSuccessfully('compileJava')
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 11 execution succeeds on java 11'() {
        when:
        // language=gradle
        buildFile << '''
            javaVersions {
                libraryTarget = 11
            }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        ExecutionResult result = runTasksSuccessfully('runMainClass')
        result.standardOutput.contains 'jdk11 features on runtime 11'
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 11 execution succeeds on java 17'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 11
            runtime = 17
        }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        ExecutionResult result = runTasksSuccessfully('runMainClass')
        result.standardOutput.contains 'jdk11 features on runtime 17'
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: when setupJdkToolchains=true toolchains are configured by jdks-latest'() {
        // language=gradle
        buildFile << '''
        apply plugin: 'com.palantir.jdks.latest'

        javaVersions {
            libraryTarget = 11
            runtime = 21
            setupJdkToolchains = true
        }
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(11)
                vendor = JvmVendorSpec.ADOPTIUM
            }
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
                vendor = JvmVendorSpec.ADOPTIUM
            }
        }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        when:
        ExecutionResult compileJavaResult = runTasksSuccessfully('compileJava')

        then:
        extractCompileToolchain(compileJavaResult.standardOutput).contains("amazon-corretto-11")
        assertBytecodeVersion(compiledClass, JAVA_11_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        when:
        ExecutionResult runResult = runTasksSuccessfully('runMainClass')

        then:
        runResult.wasUpToDate('compileJava')
        extractRunJavaCommand(runResult.standardOutput).contains("amazon-corretto-21.")

        then:
        runTasksSuccessfully('compileJava', 'run', '-Porg.gradle.java.installations.auto-detect=false', '-Porg.gradle.java.installations.auto-download=false')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: when setupJdkToolchains=false no toolchains are configured by gradle-baseline'() {
        when:
        // language=gradle
        buildFile << '''
        apply plugin: 'com.palantir.jdks.latest'
        
        javaVersions {
            libraryTarget = 11
            runtime = 21
            setupJdkToolchains = false
        }
        
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(11)
                vendor = JvmVendorSpec.ADOPTIUM
            }
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
                vendor = JvmVendorSpec.ADOPTIUM
            }
        }
        '''.stripIndent(true)
        mainJava << java11CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        runTasksWithFailure('compileJava', 'run',
                '-Porg.gradle.java.installations.auto-detect=false',
                '-Porg.gradle.java.installations.auto-download=false')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 8 execution succeeds on java 8'() {
        Assumptions.assumeThat(System.getProperty("os.arch")).describedAs(
                "On an M1 mac, this test will fail to download https://api.adoptopenjdk.net/v3/binary/latest/8/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk")
                .isNotEqualTo("aarch64");
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 8
        }
        '''.stripIndent(true)
        mainJava << java8CompatibleCode

        then:
        ExecutionResult result = runTasksSuccessfully('runMainClass')
        result.standardOutput.contains 'jdk8 features on runtime 1.8'

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: java 8 execution succeeds on java 11'() {
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
        mainJava << java8CompatibleCode
        File compiledClass = new File(projectDir, "build/classes/java/main/Main.class")

        then:
        ExecutionResult result = runTasksSuccessfully('runMainClass')
        result.standardOutput.contains 'jdk8 features on runtime 11'
        assertBytecodeVersion(compiledClass, JAVA_8_BYTECODE, NOT_ENABLE_PREVIEW_BYTECODE)

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: JavaPluginConvention.getTargetCompatibility() produces the runtime java version'() {
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

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: verification should fail when target exceeds the runtime version'() {
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

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: verification should fail when --enable-preview is on, but versions differ'() {
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

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: verification should fail when runtime does not use --enable-preview but compilation does'() {
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

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: verification should succeed when target and runtime versions match'() {
        when:
        buildFile << '''
        javaVersions {
            libraryTarget = 17
            runtime = 17
        }
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('checkJavaVersions')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: can configure a jdk path to be used'() {
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

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: checkRuntimeClasspathCompatible fails when there is a 17 jar on the runtimeClasspath but runtime is 11'() {
        fork = false

        // language=gradle
        buildFile << '''
            javaVersions {
                libraryTarget = 11
                runtime = 11
            }
            
            configurations {
                java17jar
            }
            
            dependencies {
                // This has java 17 class files and is a multi-release jar with java 21 class files 
                java17jar 'org.springframework:spring-core:6.1.5'
                implementation files(configurations.java17jar)
            }
        '''.stripIndent(true)

        when:
        def rootCause = Throwables.getRootCause(runTasksWithFailure('checkRuntimeClasspathCompatible').failure).message

        then:
        rootCause.contains('spring-core-6.1.5.jar')
        rootCause.contains('spring-jcl-6.1.5.jar')
        rootCause.contains('bytecode major version 61')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: checkRuntimeClasspathCompatible succeeds when there is only jars of the compatible java runtime versions on the runtimeClasspath'() {
        fork = false

        when:
        // language=gradle
        buildFile << '''
            javaVersions {
                libraryTarget = 8
                runtime = 8
            }
            
            dependencies {
                implementation 'com.fasterxml.jackson.core:jackson-core:2.16.1'
            }
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('checkRuntimeClasspathCompatible')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: checkRuntimeClasspathCompatible handles gradleApi'() {
        fork = false

        when:
        // language=gradle
        buildFile << '''
            javaVersions {
                libraryTarget = 8
                runtime = 8
            }
            
            dependencies {
                // this has relocated multi-version jar classes that have not been put in the right place (at least
                // for the versions of gradle used when tested). eg:
                // gradle-api-7.5.1.jar: org/gradle/internal/impldep/META-INF/versions/9/module-info.class has bytecode major version 53
                implementation gradleApi()
            }
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('checkRuntimeClasspathCompatible', '--write-locks')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
    }

    def '#gradleVersionNumber: checkRuntimeClasspathCompatible is a dependency of check'() {
        fork = false

        when:
        def stdout = runTasksSuccessfully('check', '--dry-run').standardOutput

        then:
        stdout.contains(':checkRuntimeClasspathCompatible')

        where:
        gradleVersionNumber << GRADLE_TEST_VERSIONS
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

            assert majorBytecodeVersion == expectedMajorBytecodeVersion
            assert minorBytecodeVersion == expectedMinorBytecodeVersion
        }
    }

    private static String extractCompileToolchain(String output) {
        Matcher compileMatcher = Pattern.compile("^Compiling with toolchain '([^']*)'", Pattern.MULTILINE).matcher(output)
        compileMatcher.find()
        return compileMatcher.group(1)
    }

    private static String extractRunJavaCommand(String output) {
        Matcher matcher =  Pattern.compile("^Starting process 'command '([^']*)/bin/java''.*Main", Pattern.MULTILINE).matcher(output)
        matcher.find()
        return matcher.group(1)
    }
}
