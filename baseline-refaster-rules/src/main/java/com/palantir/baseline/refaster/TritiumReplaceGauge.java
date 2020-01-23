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

import com.codahale.metrics.Gauge;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

/**
 * Remove unnecessary 'remove' invocation prior to a 'registerWithReplacement'. This refaster rule pairs with the
 * 'UnsafeGaugeRegistration' error-prone rule to replace 'registry.remove(name); registry.gauge(name, value);' with a
 * single 'registerWithReplacement' call. This refaster rule intentionally doesn't check the 'gauge' method in order to
 * avoid creating changes that don't compile when older versions of Tritium are present.
 */
public final class TritiumReplaceGauge<T> {

    @BeforeTemplate
    void before(TaggedMetricRegistry registry, MetricName name, Gauge<T> gauge) {
        registry.remove(name);
        registry.registerWithReplacement(name, gauge);
    }

    @AfterTemplate
    void after(TaggedMetricRegistry registry, MetricName name, Gauge<T> gauge) {
        registry.registerWithReplacement(name, gauge);
    }
}
