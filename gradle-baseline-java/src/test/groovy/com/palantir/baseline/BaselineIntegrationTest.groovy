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


import spock.lang.Unroll

class BaselineIntegrationTest extends AbstractPluginTest {

    def standardBuildFile() {
        """
        plugins {
            id 'com.palantir.baseline'
        }
    
        repositories {
            jcenter()
        }
        """.stripIndent()
    }

    @Unroll("Can apply on #gradleVersion")
    def canApplyOnGradle() {
        buildFile << standardBuildFile()
        multiProject.addSubproject("java-project", "apply plugin: 'java'")
        multiProject.addSubproject("other-project")

        expect:
        with().withArguments('-s').withGradleVersion(gradleVersion).build()

        where:
        gradleVersion << ['5.0', '6.0-20190904072820+0000']
    }
}
