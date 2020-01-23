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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.errorprone.refaster.Refaster;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.util.Arrays;

public final class AddAllArrayToBuilder<E> {

    @BeforeTemplate
    ImmutableCollection.Builder<E> addAllAsList(ImmutableCollection.Builder<E> builder, E[] elements) {
        return builder.addAll(
                Refaster.anyOf(Arrays.asList(elements), ImmutableList.copyOf(elements), Lists.newArrayList(elements)));
    }

    @AfterTemplate
    ImmutableCollection.Builder<E> addAll(ImmutableCollection.Builder<E> builder, E[] elements) {
        return builder.add(elements);
    }
}
