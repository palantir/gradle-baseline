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

package com.palantir.baseline.refaster;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.util.Collection;
import java.util.Set;

/**
 * {@link Set#copyOf(Collection)} and {@link java.util.Map#copyOf(java.util.Map)} in recent Java versions (checked
 * against Java 11 and Java 14) are array based collections with linear lookup. This means that they're error prone,
 * since unlike most other standard maps and sets which are O(1) or O(lg n) lookup, these structures are O(n) lookup.
 * We choose to avoid such methods to avoid hard to diagnose performance issues due to unnecessary usage. Set.of()
 * and Map.of() variants are probably acceptable since they are expected to be provided as varargs.
 */
public final class SetCopyOf<T> {

    @BeforeTemplate
    Set<T> usesJdkCopyOf(Collection<? extends T> collection) {
        return Set.copyOf(collection);
    }

    @AfterTemplate
    Set<T> usesGuavaCopyOf(Collection<? extends T> collection) {
        return ImmutableSet.copyOf(collection);
    }
}
