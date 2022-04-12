/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions

import nebula.test.ProjectSpec
import spock.lang.Unroll

class JdkDownloaderSpec extends ProjectSpec {
    @Unroll
    def '#os-#arch: can download a jdk from an Azul CDN'() {
        AzulJdkDownloader jdkDownloader = new AzulJdkDownloader(project)

        when:
        def path = jdkDownloader.downloadJdkFor(JdkSpec.builder()
                .javaVersion('11.0.14.1')
                .zuluVersion('11.54.25')
                .os(os)
                .arch(arch)
                .build())

        then:
        println path
        path.toFile().exists()
        path.getFileName().toString().endsWith('.zip')

        where:
        os << ['macosx', 'linux', 'windows']
        arch << ['aarch64', 'x64', 'x64']
    }
}
