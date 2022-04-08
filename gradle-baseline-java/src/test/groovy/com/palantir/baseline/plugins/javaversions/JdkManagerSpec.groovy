/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions

import nebula.test.ProjectSpec

class JdkManagerSpec extends ProjectSpec {
    def 'can download a jdk from an Azul CDN'() {
        JdkManager jdkManager = new JdkManager(project.getBuildDir().toPath().resolve("jdks"), new AzulJdkDownloader(project))

        when:
        def path = jdkManager.jdk(JdkSpec.builder()
                .javaVersion('11.0.9.1')
                .zuluVersion('11.43.1021')
                .os('macosx')
                .arch('aarch64')
                .build())

        then:
        println path

        def javaVersionOutput = ['sh', '-c', "${path}/bin/java -version 2>&1"].execute().text
        javaVersionOutput.contains 'Zulu11.43+1021-CA'
    }

    boolean deleteProjectDir(){
        return false
    }
}
