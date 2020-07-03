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

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import java.util.Map;

/**
 * See {@link SetCopyOf}.
 */
public final class MapCopyOf<K, V> {

    @BeforeTemplate
    Map<K, V> usesJdkCopyOf(Map<? extends K, ? extends V> someMap) {
        return Map.copyOf(someMap);
    }

    @AfterTemplate
    Map<K, V> usesGuavaCopyOf(Map<? extends K, ? extends V> someMap) {
        return ImmutableMap.copyOf(someMap);
    }
}
