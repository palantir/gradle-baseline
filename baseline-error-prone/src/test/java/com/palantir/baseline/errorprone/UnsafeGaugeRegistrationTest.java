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

package com.palantir.baseline.errorprone;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class UnsafeGaugeRegistrationTest {

    @Test
    void testFix() {
        RefactoringValidator.of(new UnsafeGaugeRegistration(), getClass())
                .addInputLines(
                        "Test.java",
                        "import com.palantir.tritium.metrics.registry.MetricName;",
                        "import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;",
                        "class Test {",
                        "   void f(TaggedMetricRegistry registry, MetricName name) {",
                        "       registry.gauge(name, () -> 1);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tritium.metrics.registry.MetricName;",
                        "import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;",
                        "class Test {",
                        "   void f(TaggedMetricRegistry registry, MetricName name) {",
                        "       registry.registerWithReplacement(name, () -> 1);",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testKnownBug() {
        RefactoringValidator.of(new UnsafeGaugeRegistration(), getClass())
                .addInputLines(
                        "Test.java",
                        "import com.codahale.metrics.Gauge;",
                        "import com.palantir.tritium.metrics.registry.MetricName;",
                        "import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;",
                        "class Test {",
                        "   void f(TaggedMetricRegistry registry, MetricName name, Gauge<?> gauge) {",
                        "       registry.gauge(name, gauge);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.codahale.metrics.Gauge;",
                        "import com.palantir.tritium.metrics.registry.MetricName;",
                        "import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;",
                        "class Test {",
                        "   void f(TaggedMetricRegistry registry, MetricName name, Gauge<?> gauge) {",
                        //      Tests our workaround for https://github.com/google/error-prone/issues/1451
                        "       registry.registerWithReplacement(name, gauge);",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    @Test
    void testNegative() {
        CompilationTestHelper.newInstance(UnsafeGaugeRegistration.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import com.codahale.metrics.Gauge;",
                        "import com.palantir.tritium.metrics.registry.MetricName;",
                        "import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;",
                        "class Test {",
                        "   Gauge<?> f(TaggedMetricRegistry registry, MetricName name) {",
                        "       return registry.gauge(name, () -> 1);",
                        "   }",
                        "}")
                .doTest();
    }
}
