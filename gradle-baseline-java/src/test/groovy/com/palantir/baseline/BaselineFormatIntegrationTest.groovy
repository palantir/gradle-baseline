/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.NameFileFilter
import org.apache.commons.io.filefilter.NotFileFilter
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import static org.assertj.core.api.Assertions.assertThat

class BaselineFormatIntegrationTest extends AbstractPluginTest {

    def setup() {
        FileUtils.copyDirectory(
                new File("../gradle-baseline-java-config/resources"),
                new File(projectDir, ".baseline"))
    }

    def standardBuildFile = '''
        plugins {
            id 'java'
            id 'com.palantir.baseline-format'
        }
    '''.stripIndent()

    def noJavaBuildFile = '''
        plugins {
            id 'com.palantir.baseline-format'
        }
    '''.stripIndent()

    def validJavaFile = '''
    package test;
    
    public class Test {
        void test() {
            int x = 1;
            System.out.println(
                    "Hello");
            Optional.of("hello").orElseGet(() -> {
                return "Hello World";
            });
        }
    }
    '''.stripIndent()

    def invalidJavaFile = '''
    package test;
    import com.java.unused;
    public class Test { void test() {int x = 1;
        System.out.println(
            "Hello"
        );
        Optional.of("hello").orElseGet(() -> { 
            return "Hello World";
        });
    } }
    '''.stripIndent()


    def 'can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        with('format', '--stacktrace').build()
    }

    def 'eclipse formatter integration test'() {
        def inputDir = new File("src/test/resources/com/palantir/baseline/formatter-in")
        def expectedDir = new File("src/test/resources/com/palantir/baseline/eclipse-formatter-expected")

        def testedDir = new File(projectDir, "src/main/java")
        FileUtils.copyDirectory(inputDir, testedDir)

        buildFile << """
            plugins {
                id 'java'
                id 'com.palantir.baseline-format'
            }
        """.stripIndent()
        file('gradle.properties') << "com.palantir.baseline-format.eclipse=true\n"

        when:
        BuildResult result = with(':format').build()
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS

        then:
        assertThatFilesAreTheSame(testedDir, expectedDir)
    }

    def 'palantir java format works'() {
        def inputDir = new File("src/test/resources/com/palantir/baseline/formatter-in")
        def expectedDir = new File("src/test/resources/com/palantir/baseline/palantirjavaformat-expected")

        def testedDir = new File(projectDir, "src/main/java")
        FileUtils.copyDirectory(inputDir, testedDir)

        buildFile << """
            plugins {
                id 'java'
                id 'com.palantir.baseline-format'
            }
        """.stripIndent()
        file('gradle.properties') << "com.palantir.baseline-format.palantir-java-format=true\n"

        when:
        BuildResult result = with(':format').build()

        then:
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
        assertThatFilesAreTheSame(testedDir, expectedDir)
    }

    private static void assertThatFilesAreTheSame(File outputDir, File expectedDir) throws IOException {
        Collection<File> files = listJavaFilesRecursively(outputDir)

        for (File file : files) {
            // The files are created inside the `projectDir`
            def path = file.toPath()
            Path relativized = outputDir.toPath().relativize(path)
            Path expectedFile = expectedDir.toPath().resolve(relativized)
            if (Boolean.getBoolean("recreate")) {
                Files.createDirectories(expectedFile.getParent())
                Files.deleteIfExists(expectedFile)
                Files.copy(path, expectedFile)
            }
            assertThat(path).hasSameContentAs(expectedFile)
        }
    }

    private static Collection<File> listJavaFilesRecursively(File dir) {
        def excludedDirectories = ["build", ".gradle", ".baseline"]
        return FileUtils.listFiles(
                dir,
                new SuffixFileFilter(".java"),
                new NotFileFilter(new NameFileFilter(excludedDirectories)))
    }

    def 'cannot run format task when java plugin is missing'() {
        when:
        buildFile << noJavaBuildFile

        then:
        with('format', '--stacktrace').buildAndFail()
    }

    def 'format task works on new source sets'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            sourceSets { foo }
        '''.stripIndent()
        file('src/foo/java/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('format', '-Pcom.palantir.baseline-format.eclipse').build()
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
        file('src/foo/java/test/Test.java').text == validJavaFile
    }

    def 'format task works on other language java sources'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            apply plugin: 'groovy'
            sourceSets { foo }
        '''.stripIndent()
        file('src/foo/groovy/test/Test.java') << invalidJavaFile

        then:
        BuildResult result = with('format', '-Pcom.palantir.baseline-format.eclipse').build()
        result.task(":format").outcome == TaskOutcome.SUCCESS
        result.task(":spotlessApply").outcome == TaskOutcome.SUCCESS
        file('src/foo/groovy/test/Test.java').text == validJavaFile
    }

    def 'format ignores generated files'() {
        when:
        buildFile << standardBuildFile
        buildFile << '''
            sourceSets {
                main {
                    java { srcDir 'src/generated/java' }
                }
            }
            
            // ensure file is in the source set
            sourceSets.main.allJava.filter { it.name == "Test.java" }.singleFile
        '''.stripIndent()
        def javaFileContents = '''
            package test;
            import java.lang.Void;
            public class Test { Void test() {} }
        '''.stripIndent()
        file('src/generated/java/test/Test.java') << javaFileContents

        then:
        BuildResult result = with('spotlessJavaCheck').build()
        result.task(":spotlessJava").outcome == TaskOutcome.SUCCESS
    }

    def 'formatDiff updates only relevant chunks of files'() {
        when:
        buildFile << standardBuildFile
        "git init".execute(Collections.emptyList(), projectDir)
        "git config user.name Foo".execute(Collections.emptyList(), projectDir)
        "git config user.email foo@bar.com".execute(Collections.emptyList(), projectDir)

        file('src/main/java/Main.java') << '''
        class Main {
            public static void main(String... args) {

            }
        }
        '''.stripIndent()

        "git commit --allow-empty -m Commit".execute(Collections.emptyList(), projectDir)

        file('src/main/java/Main.java').text = '''
        class Main {
            public static void main(String... args) {
                                        System.out.println("Reformat me please");
            }
        }
        '''.stripIndent()

        then:
        with('formatDiff', '-Pcom.palantir.baseline-format.palantir-java-format').build()
        file('src/main/java/Main.java').text == '''
        class Main {
            public static void main(String... args) {
                System.out.println("Reformat me please");
            }
        }
        '''.stripIndent()
    }
}
