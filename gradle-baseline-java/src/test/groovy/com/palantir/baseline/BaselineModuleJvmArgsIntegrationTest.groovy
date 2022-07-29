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
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipOutputStream

class BaselineModuleJvmArgsIntegrationTest extends IntegrationSpec {

    def standardBuildFile = '''
    plugins {
        id 'java-library'
        id 'application'
    }

    apply plugin: 'com.palantir.baseline-java-versions'
    apply plugin: 'com.palantir.baseline-module-jvm-args'
    
    javaVersions {
        libraryTarget = 11
        runtime = 17
    }

    repositories {
        mavenCentral()
    }
    '''.stripIndent(true)

    def setup() {
        setFork(true)
        buildFile << standardBuildFile
    }

    def 'Compiles with locally defined exports'() {
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
    }

    def 'Compiles with locally defined opens'() {
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
    }

    def 'Builds javadoc with locally defined exports'() {
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
    }

    def 'Builds javadoc with locally defined opens'() {
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
    }

    def 'Runs with locally defined exports'() {
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
    }

    def 'Runs with locally defined exports with the release plugin, not toolchains'() {
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
    }

    def 'Runs with locally defined opens'() {
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
    }

    def 'Adds locally defined exports to the jar manifest'() {
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
    }

    def 'Adds Baseline-Enable-Preview attribute to jar manifest'() {
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
    }

    def 'Executes with externally defined exports'() {
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
    }

    def 'Handles jars with no manifest'() {
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
    }

    def 'Does not add externally defined exports to the jar manifest'() {
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
    }

    def 'Validates exports'() {
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
    }
}
