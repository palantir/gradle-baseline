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


    def 'can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        with('format', '--stacktrace').build()
    }

    def 'eclipse formatter integration test'() {
        def inputDir = new File("src/test/resources/com/palantir/baseline/formatter-in")
        def expectedDir = new File("src/test/resources/com/palantir/baseline/formatter-expected")

        FileUtils.copyDirectory(inputDir, projectDir)

        when:
        BuildResult result = with('format').build()
        result.task(":java:format").outcome == TaskOutcome.SUCCESS
        result.task(":java:spotlessApply").outcome == TaskOutcome.SUCCESS

        then:
        assertThatFilesAreTheSame(projectDir, expectedDir)
    }

    private static void assertThatFilesAreTheSame(File outputDir, File expectedDir) throws IOException {
        def excludedDirectories = ["build", ".gradle", ".baseline"]
        def files = FileUtils.listFiles(
                outputDir,
                new SuffixFileFilter(".java"),
                new NotFileFilter(new NameFileFilter(excludedDirectories)))

        for (File file : files) {
            // The files are created inside the `projectDir`
            def path = file.toPath()
            Path relativized = outputDir.toPath().relativize(path)
            Path expectedFile = expectedDir.toPath().resolve(relativized)
            if (Boolean.valueOf(System.getProperty("recreate", "false"))) {
                Files.createDirectories(expectedFile.getParent())
                Files.deleteIfExists(expectedFile)
                Files.copy(path, expectedFile)
            }
            assertThat(path).hasSameContentAs(expectedFile)
        }
    }

    def 'cannot run format task when java plugin is missing'() {
        when:
        buildFile << noJavaBuildFile

        then:
        with('format', '--stacktrace').buildAndFail()
    }
}
