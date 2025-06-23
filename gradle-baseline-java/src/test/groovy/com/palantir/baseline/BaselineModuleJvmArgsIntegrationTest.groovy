/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.MoreCollectors
import com.palantir.gradle.plugintesting.GradleTestVersions
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipOutputStream

@Unroll
class BaselineModuleJvmArgsIntegrationTest extends IntegrationSpec {
    // language=Gradle
    def standardBuildFile = '''
    plugins {
        id 'java-library'
        id 'application'
    }

    apply plugin: 'com.palantir.baseline-java-versions'
    apply plugin: 'com.palantir.baseline-module-jvm-args'
    
    javaVersions {
        // Use the same version at the current test runtime so that we definitely have a JDK of this
        // version available for Gradle's built in java toolchains
        libraryTarget = Runtime.version().version().get(0)
    }

    repositories {
        mavenCentral()
    }
    '''.stripIndent(true)

    def setup() {
        buildFile << standardBuildFile
    }

    def '#gradleVersionNumber: Compiles with locally defined exports'() {
        gradleVersion = gradleVersionNumber
        
        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        moduleJvmArgs {
           exports = ['jdk.compiler/com.sun.tools.javac.code']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                com.sun.tools.javac.code.Symbol.class.toString();
            }
        }
        '''.stripIndent(true))

        then:
        runTasksSuccessfully('compileJava')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Compiles with locally defined opens'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        moduleJvmArgs {
           opens = ['jdk.compiler/com.sun.tools.javac.code']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                com.sun.tools.javac.code.Symbol.class.toString();
            }
        }
        '''.stripIndent(true))

        then:
        runTasksSuccessfully('compileJava')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Builds javadoc with locally defined exports'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        moduleJvmArgs {
           exports = ['jdk.compiler/com.sun.tools.javac.code']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            /**
             * Javadoc {@link com.sun.tools.javac.code.Symbol}.
             * @param args Program arguments
             */
            public static void main(String[] args) {
                com.sun.tools.javac.code.Symbol.class.toString();
            }
        }
        '''.stripIndent(true))

        then:
        runTasksSuccessfully('javadoc')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Builds javadoc with locally defined opens'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        moduleJvmArgs {
           opens = ['jdk.compiler/com.sun.tools.javac.code']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            /**
             * Javadoc {@link com.sun.tools.javac.code.Symbol}.
             * @param args Program arguments
             */
            public static void main(String[] args) {
                com.sun.tools.javac.code.Symbol.class.toString();
            }
        }
        '''.stripIndent(true))

        then:
        runTasksSuccessfully('javadoc')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Runs with locally defined exports'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        
        moduleJvmArgs {
           exports = ['java.management/sun.management']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        // Gradle appears to normalize args, joining '--add-exports java.management/sun.management=ALL-UNNAMED'
        // with an equals.
        result.standardOutput.contains('--add-exports=java.management/sun.management=ALL-UNNAMED')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Runs with locally defined exports with the release plugin, not toolchains'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile.text = '''
        plugins {
            id 'java-library'
            id 'application'
        }
        apply plugin: 'com.palantir.baseline-release-compatibility'
        apply plugin: 'com.palantir.baseline-module-jvm-args'
        sourceCompatibility = 11
        repositories {
            mavenCentral()
        }
        application {
            mainClass = 'com.Example'
        }
        moduleJvmArgs {
           exports = ['java.management/sun.management']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        // Gradle appears to normalize args, joining '--add-exports java.management/sun.management=ALL-UNNAMED'
        // with an equals.
        result.standardOutput.contains('--add-exports=java.management/sun.management=ALL-UNNAMED')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Runs with locally defined opens'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        
        moduleJvmArgs {
           opens 'java.management/sun.management'
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        // Gradle appears to normalize args, joining '--add-exports java.management/sun.management=ALL-UNNAMED'
        // with an equals.
        result.standardOutput.contains('--add-opens=java.management/sun.management=ALL-UNNAMED')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Adds locally defined exports to the jar manifest'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        
        moduleJvmArgs {
           exports = ['java.management/sun.management']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        then:
        runTasksSuccessfully('jar')
        JarFile jarFile = Arrays.stream(directory("build/libs").listFiles())
                .filter(file -> file.name.endsWith(".jar"))
                .map(JarFile::new)
                .collect(MoreCollectors.onlyElement())
        String manifestValue = jarFile.getManifest().getMainAttributes().getValue('Add-Exports')
        manifestValue == 'java.management/sun.management'

        !jarFile.getManifest().getMainAttributes().containsKey('Baseline-Enable-Preview')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Adds Baseline-Enable-Preview attribute to jar manifest'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        javaVersions {
            runtime = '11_PREVIEW'
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
            }
        }
        '''.stripIndent(true))

        then:
        runTasksSuccessfully('jar')
        JarFile jarFile = Arrays.stream(directory("build/libs").listFiles())
                .filter(file -> file.name.endsWith(".jar"))
                .map(JarFile::new)
                .collect(MoreCollectors.onlyElement())
        String manifestValue = jarFile.getManifest().getMainAttributes().getValue('Baseline-Enable-Preview')
        manifestValue == '11'

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Executes with externally defined exports'() {
        gradleVersion = gradleVersionNumber

        when:
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.getMainAttributes().putValue('Add-Exports', 'java.management/sun.management')
        File testJar = new File(getProjectDir(),"test.jar");
        testJar.withOutputStream { fos ->
            new JarOutputStream(fos, manifest).close()
        }
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        dependencies {
            implementation files('test.jar')
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        // Gradle appears to normalize args, joining '--add-exports java.management/sun.management=ALL-UNNAMED'
        // with an equals.
        result.standardOutput.contains('--add-exports=java.management/sun.management=ALL-UNNAMED')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Handles jars with no manifest'() {
        gradleVersion = gradleVersionNumber

        when:
        File testJar = new File(getProjectDir(),"test.jar");
        testJar.withOutputStream { fos ->
            new ZipOutputStream(fos).close()
        }
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        dependencies {
            implementation files('test.jar')
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        then:
        ExecutionResult result = runTasksSuccessfully('run')
        !result.standardOutput.contains('--add-exports')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Does not add externally defined exports to the jar manifest'() {
        gradleVersion = gradleVersionNumber

        when:
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.getMainAttributes().putValue('Add-Exports', 'java.management/sun.management')
        File testJar = new File(getProjectDir(),"test.jar");
        testJar.withOutputStream { fos ->
            new JarOutputStream(fos, manifest).close()
        }
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        dependencies {
            implementation files('test.jar')
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        then:
        runTasksSuccessfully('jar')
        JarFile jarFile = Arrays.stream(directory("build/libs").listFiles())
                .filter(file -> file.name.endsWith(".jar"))
                .map(JarFile::new)
                .collect(MoreCollectors.onlyElement())
        String manifestValue = jarFile.getManifest().getMainAttributes().getValue('Add-Exports')
        manifestValue == null

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Validates exports'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }
        
        moduleJvmArgs {
           exports = ['java.management']
        }
        '''.stripIndent(true)

        then:
        ExecutionResult result = runTasksWithFailure('jar')
        result.standardError.contains('separated by a single slash')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Task not up-to-date when extension value changes'() {
        gradleVersion = gradleVersionNumber

        when:
        buildFile << '''
        application {
            mainClass = 'com.Example'
        }

        moduleJvmArgs {
           exports = ['java.management/sun.management']
        }
        '''.stripIndent(true)
        writeJavaSourceFile('''
        package com;
        public class Example {
            public static void main(String[] args) {
                System.out.println(String.join(
                    " ",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
            }
        }
        '''.stripIndent(true))

        ExecutionResult resultBeforeChange = runTasksSuccessfully('jar')

        buildFile.text = standardBuildFile + '''
        application {
            mainClass = 'com.Example\'
        }

        moduleJvmArgs {
           exports = ['java.management/sun.management123']
        }
        '''.stripIndent(true)

        ExecutionResult resultAfterChange = runTasksSuccessfully('jar')

        then:
        !resultBeforeChange.wasUpToDate('jar')
        resultBeforeChange.wasExecuted('jar')
        !resultAfterChange.wasUpToDate('jar')
        resultAfterChange.wasExecuted('jar')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }

    def '#gradleVersionNumber: Test task picks up Add-Exports from jars added to classpath after configuration'() {
        buildFile << '''
        dependencies {
            testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.2'
            testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.2'
        }
    '''.stripIndent(true)

        writeJavaSourceFile('''
            package com;
            import org.junit.jupiter.api.Test;
            public class ExampleTest {
                @Test
                public void test() {
                    System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                }
            }
        '''.stripIndent(true), 'src/test/java/com/ExampleTest.java')

        // Create a jar with Add-Exports in the manifest
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.getMainAttributes().putValue('Add-Exports', 'java.management/sun.management')
        File testJar = new File(getProjectDir(),"test.jar");
        testJar.withOutputStream { fos ->
            new JarOutputStream(fos, manifest).close()
        }

        // Mutate classpath after configuration
        buildFile << """
            tasks.named('test').configure {
                classpath += files('test.jar')
                useJUnitPlatform()
                testLogging.showStandardStreams = true
            }
        """.stripIndent(true)

        when:
        def result = runTasksSuccessfully('test')

        then:
        // The test JVM should include the --add-exports argument from the manifest of test-addon.jar
        result.standardOutput.contains('--add-exports=java.management/sun.management=ALL-UNNAMED')

        where:
        gradleVersionNumber << GradleTestVersions.gradleVersionsForTests
    }
}
