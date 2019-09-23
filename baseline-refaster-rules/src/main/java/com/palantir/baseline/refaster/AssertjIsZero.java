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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.assertj.core.api.AbstractDoubleAssert;
import org.assertj.core.api.AbstractFloatAssert;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.NumberAssert;

public final class AssertjIsZero {

    @BeforeTemplate
    public AbstractIntegerAssert<?> before(AbstractIntegerAssert<?> input) {
        return input.isEqualTo(0);
    }

    @BeforeTemplate
    public AbstractLongAssert<?> before(AbstractLongAssert<?> input) {
        return input.isEqualTo(0L);
    }

    @BeforeTemplate
    public AbstractDoubleAssert<?> before(AbstractDoubleAssert<?> input) {
        return input.isEqualTo(0D);
    }

    @BeforeTemplate
    public AbstractFloatAssert<?> before(AbstractFloatAssert<?> input) {
        return input.isEqualTo(0f);
    }

    @BeforeTemplate
    public AbstractDoubleAssert<?> beforeWithDecimal(AbstractDoubleAssert<?> input) {
        return input.isEqualTo(0.0D);
    }

    @BeforeTemplate
    public AbstractFloatAssert<?> beforeWithDecimal(AbstractFloatAssert<?> input) {
        return input.isEqualTo(0.0);
    }

    @AfterTemplate
    public NumberAssert<?, ?> after(NumberAssert<?, ?> input) {
        return input.isZero();
    }
}
