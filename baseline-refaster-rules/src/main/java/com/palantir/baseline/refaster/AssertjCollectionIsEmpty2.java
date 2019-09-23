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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.refaster.Refaster;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.util.Collections;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractIterableAssert;

public final class AssertjCollectionIsEmpty2<A extends AbstractIterableAssert<A, I, T, E>,
        I extends Iterable<? extends T>, T, E extends AbstractAssert<E, T>> {

    @BeforeTemplate
    void before1(A in) {
        in.hasSize(0);
    }

    @BeforeTemplate
    void before2(A in) {
        in.isEqualTo(Refaster.anyOf(
                ImmutableList.of(),
                ImmutableSet.of(),
                Collections.emptySet(),
                Collections.emptyList()));
    }

    @AfterTemplate
    void after(A in) {
        in.isEmpty();
    }
}
