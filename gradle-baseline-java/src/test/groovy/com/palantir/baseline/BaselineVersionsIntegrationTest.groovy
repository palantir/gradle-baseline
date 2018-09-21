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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome

class BaselineVersionsIntegrationTest  extends AbstractPluginTest {

    def bomVersion = "42.42.42"

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
            compile 'org.scala-lang:scala-library'
        }
        
        dependencyRecommendations {
            mavenBom module: 'com.palantir.product:your-bom:${bomVersion}'
        }
        """.stripIndent()
    }

    def setup() {
        String bomContent =
            """<?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.palantir.product</groupId>
                  <artifactId>your-bom</artifactId>
                  <version>42.42.42</version>
                  <dependencies>
                  </dependencies>
                  <name>your-bom</name>
                  <description/>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.palantir.product</groupId>
                        <artifactId>your-bom</artifactId>
                        <version>${bomVersion}</version>
                      </dependency>
                      <dependency>
                        <groupId>org.scala-lang</groupId>
                        <artifactId>scala-library</artifactId>
                        <version>2.12.5</version>
                      </dependency>
                      <dependency>
                        <groupId>org.scala-lang</groupId>
                        <artifactId>scala-compiler</artifactId>
                        <version>2.12.5</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
            """.stripIndent()
        Path bomPath = Paths.get(projectDir.toString(), "maven", "com", "palantir", "product", "your-bom", bomVersion)
        Files.createDirectories(bomPath)
        Files.write(bomPath.resolve("your-bom-${bomVersion}.pom"), bomContent.getBytes(StandardCharsets.UTF_8))
    }

    def setupVersionsProps(String propsContent) {
        Files.write(projectDir.toPath().resolve("versions.props"), propsContent.getBytes(StandardCharsets.UTF_8))
    }

    def buildSucceed() {
        BuildResult result = with('--info', 'checkVersionsProps').build()
        result.task(':checkVersionsProps').outcome == TaskOutcome.SUCCESS
        result
    }

    def buildAndFailWith(String error) {
        BuildResult result = with('checkVersionsProps', '--stacktrace').buildAndFail()
        result.output.contains(error)
    }

    def 'Override version conflict should succeed'() {
        when:
        setupVersionsProps("org.scala-lang:scala-library = 2.12.6")
        buildFile << standardBuildFile(projectDir)

        then:
        def result = buildSucceed()
        result.output.contains("There are conflicts between versions.props and the bom:\n" +
                "  versions.props: org.scala-lang:scala-library -> 2.12.6\n" +
                "  bom:            org.scala-lang:scala-library -> 2.12.5")
    }

    def 'Task should run as part of :check'() {
        when:
        buildFile << standardBuildFile(projectDir)

        then:
        def result = with('check', '--stacktrace').build()
        result.task(':checkVersionsProps').outcome == TaskOutcome.SUCCESS
    }

    def 'Same version conflict should fail'() {
        when:
        setupVersionsProps("org.scala-lang:scala-library = 2.12.5")
        buildFile << standardBuildFile(projectDir)

        then:
        buildAndFailWith("Critical conflicts between versions.props and the bom (overriding with same version)")

    }

    def 'Same version conflict but wildcard override at least one should succeed'() {
        when:
        setupVersionsProps("org.scala-lang:scala-* = 2.12.5")
        buildFile << standardBuildFile(projectDir) + """
        dependencies {
            compile 'org.scala-lang:scala-reflect'
            compile 'org.scala-lang:scala-compiler'
        }"""

        then:
        def result = buildSucceed()
        result.output.contains("pin required by other non recommended artifacts: [org.scala-lang:scala-reflect]")
        result.output.contains("  bom:\n" +
                "                  org.scala-lang:scala-compiler -> 2.12.5\n" +
                "                  org.scala-lang:scala-library -> 2.12.5")

    }

    def 'Version props conflict without exact artifact should fail'() {
        when:
        setupVersionsProps("com.fasterxml.jackson.*:* = 2.9.6\ncom.fasterxml.jackson.core:* = 2.9.5")
        buildFile << standardBuildFile(projectDir) + """
        dependencies {
            compile 'com.fasterxml.jackson.core:jackson-core'
        }"""

        then:
        buildAndFailWith("")
    }

    def 'Version props conflict with exact artifact should succeed'() {
        when:
        setupVersionsProps("com.fasterxml.jackson.*:* = 2.9.6\ncom.fasterxml.jackson.core:jackson-core = 2.9.5")
        buildFile << standardBuildFile(projectDir) + """
        dependencies {
            compile 'com.fasterxml.jackson.core:jackson-core'
        }"""

        then:
        buildSucceed()
    }

    def 'Unused version should fail'() {
        when:
        setupVersionsProps("notused:atall = 42.42")
        buildFile << standardBuildFile(projectDir)

        then:
        buildAndFailWith("There are unused pins in your versions.props")
    }

    def 'recommending bom version shouldn\'t fail even if bom recommends itself'() {
        when:
        setupVersionsProps("org.scala-lang:scala-library = 2.12.6\ncom.palantir.product:your-bom = ${bomVersion}")
        buildFile <<
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
            compile 'org.scala-lang:scala-library'
        }

        dependencyRecommendations {
            mavenBom module: 'com.palantir.product:your-bom'
        }
        """.stripIndent()

        then:
        buildSucceed()
    }

}
