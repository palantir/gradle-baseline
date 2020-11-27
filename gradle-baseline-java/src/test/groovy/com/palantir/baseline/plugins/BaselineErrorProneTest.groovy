/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins

import java.util.regex.Pattern
import spock.lang.Specification

class BaselineErrorProneTest extends Specification {
    void testExcludedPaths() {
        when:
        String excludedPaths = BaselineErrorProne.excludedPathsRegex()
        def predicate = Pattern.compile(excludedPaths).asPredicate()

        then:
        predicate.test 'tritium-core/build/metricSchema/generated_src'
        predicate.test 'tritium-registry/generated_src/com/palantir/tritium/metrics/registry/ImmutableMetricName.java'
        predicate.test 'tritium-metrics/build/metricSchema/generated_src/com/palantir/tritium/metrics/TlsMetrics.java'
        predicate.test 'tritium-jmh/generated_testSrc/com/palantir/tritium/microbenchmarks/generated/ProxyBenchmark_jmhType.java'
    }
}
