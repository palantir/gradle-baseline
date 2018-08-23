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
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.12</version>
                      </dependency>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
            """.stripIndent()
        Path bomPath = Paths.get(projectDir.toString(), "maven", "com", "palantir", "product", "your-bom", version)
        java.nio.file.Files.createDirectories(bomPath)
        Files.write(bomContent.getBytes(StandardCharsets.UTF_8), bomPath.resolve("your-bom-${version}.pom").toFile())
    }

    def setupVersionsProps(String propsContent) {
        Files.write(propsContent.getBytes(StandardCharsets.UTF_8), projectDir.toPath().resolve("versions.props").toFile())
    }

    def setup() {
        setupBom()
    }

    def checkVersionsPropsSucceed() {
        try {
            with('checkVersionsProps', '--stacktrace').build()
            ""
        } catch (Exception e) {
            e.getMessage()
        }
    }

    def 'Override version conflict should succeed'() {
        when:
        setupVersionsProps("junit:junit = 4.11")
        buildFile << standardBuildFile(projectDir)

        then:
        checkVersionsPropsSucceed() == ""
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
        setupVersionsProps("junit:junit = 4.12")
        buildFile << standardBuildFile(projectDir)

        then:
            checkVersionsPropsSucceed()
                    .contains("Critical conflicts between versions.props and the bom (overriding with same version)")

    }

    def 'Same version conflict but wildcard override at least one should succeed'() {
        when:
        setupVersionsProps("junit:* = 4.12")
        buildFile << standardBuildFile(projectDir)

        then:
        checkVersionsPropsSucceed() == ""

    }

    def 'Unused version should fail'() {
        when:
        setupVersionsProps("notused:atall = 4.12")
        buildFile << standardBuildFile(projectDir)

        then:
        checkVersionsPropsSucceed()
                .contains("There are unused pins in your versions.props")
    }

}
