/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.plugintesting.ConfigurationCacheSpec

class ConfigurationCacheTest extends ConfigurationCacheSpec {

    def setup() {
        definePluginOutsideOfPluginBlock = true
        keepFiles = true
        // language=Gradle
        buildFile << """
             buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    gradlePluginPortal() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                    mavenLocal()
                }
                 dependencies {
                     classpath 'com.palantir.gradle.consistentversions:gradle-consistent-versions:3.18.0'
                 }
             }
         
            apply plugin: 'com.palantir.consistent-versions'
            apply plugin: 'com.palantir.baseline'
            apply plugin: 'com.palantir.java-format'
            apply plugin: 'java'
        
            repositories {
                mavenCentral()
                mavenLocal()
            }
        """.stripIndent(true)

        file("versions.props")
        file("versions.lock")
    }

    def "classes task runs with configuration cache without issues"() {
        expect:
        runTasksWithConfigurationCacheAndCheck("classes")
    }
}
