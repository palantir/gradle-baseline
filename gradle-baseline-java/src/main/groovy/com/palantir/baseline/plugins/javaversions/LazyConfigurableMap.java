/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins.javaversions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.gradle.api.Action;

final class LazyConfigurableMap<K, V> {
    private final Supplier<V> valueFactory;
    private final List<LazyEntry<K, V>> entries = new ArrayList<>();
    private final Map<K, V> computedValues = new HashMap<>();

    LazyConfigurableMap(Supplier<V> valueFactory) {
        this.valueFactory = valueFactory;
    }

    public void put(K key, Action<V> value) {
        entries.add(0, requestedKey -> {
            if (requestedKey.equals(key)) {
                return Optional.of(value);
            }

            return Optional.empty();
        });
    }

    public void put(LazyEntry<K, V> lazyEntry) {
        entries.add(0, lazyEntry);
    }

    public V get(K key) {
        return computedValues.computeIfAbsent(key, _ignored -> {
            V value = valueFactory.get();
            entries.forEach(lazyEntry -> {
                lazyEntry.blah(key).ifPresent(action -> action.execute(value));
            });
            return value;
        });
    }

    interface LazyEntry<K, V> {
        Optional<Action<V>> blah(K key);
    }
}
