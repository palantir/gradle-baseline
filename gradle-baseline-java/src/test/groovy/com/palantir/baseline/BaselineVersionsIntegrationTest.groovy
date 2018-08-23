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

import com.google.common.io.Files
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths;
import org.gradle.testkit.runner.TaskOutcome

class BaselineVersionsIntegrationTest  extends AbstractPluginTest {

    def version = "42.42.42"

    def standardBuildFile(File projectDir) {
        """
        plugins {
            id 'java'
            id 'com.palantir.baseline-versions'
        }
    
        repositories {
            mavenCentral()
            maven { url "file://${projectDir.absolutePath}/maven" }
        }
        
        dependencies {
            compile 'junit:junit'
        }
        
        dependencyRecommendations {
            mavenBom module: 'com.palantir.product:your-bom:${version}'
        }
        """.stripIndent()
    }

    def setupBom() {
        String bomContent = """
            42
        """
        Path bomPath = Paths.get(projectDir.toString(), "maven", "com", "palantir", "product", "your-bom", version)
        java.nio.file.Files.createDirectories(bomPath)
        Files.write(bomContent.getBytes(StandardCharsets.UTF_8), bomPath.resolve("your-bom-${version}.pom").toFile())
    }

    def setupProps() {
        String propsContent = """
            42
        """
        Files.write(propsContent.getBytes(StandardCharsets.UTF_8), projectDir.toPath().resolve("versions.props").toFile())
    }

    def setup() {
        setupBom()
        setupProps()
    }

    def 'Task should run as part of :check'() {
        when:
        buildFile << standardBuildFile(projectDir)

        then:
        def result = with('check', '--stacktrace').build()
        println result.output
        result.task(':checkVersionsProps').outcome == TaskOutcome.SUCCESS
    }
}
