/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.refaster;

import static org.assertj.core.api.Assumptions.assumeThat;

import org.junit.Test;

public class TritiumReplaceGaugeTest {

    @Test
    public void test() {
        assumeThat(System.getProperty("java.specification.version"))
                .describedAs("Refaster does not fully support java 11")
                .isEqualTo("1.8");
        RefasterTestHelper.forRefactoring(TritiumReplaceGauge.class)
                .withInputLines(
                        "Test",
                        "import com.codahale.metrics.Gauge;",
                        "import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;",
                        "import com.palantir.tritium.metrics.registry.MetricName;",
                        "public class Test {",
                        "  void f(DefaultTaggedMetricRegistry registry) {",
                        "    MetricName name = MetricName.builder().safeName(\"foo\").build();",
                        "    registry.remove(name);",
                        "    registry.registerWithReplacement(name, () -> 1);",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import com.codahale.metrics.Gauge;",
                        "import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;",
                        "import com.palantir.tritium.metrics.registry.MetricName;",
                        "public class Test {",
                        "  void f(DefaultTaggedMetricRegistry registry) {",
                        "    MetricName name = MetricName.builder().safeName(\"foo\").build();",
                        "    registry.registerWithReplacement(name, () -> 1);",
                        "    ",
                        "  }",
                        "}");
    }
}
