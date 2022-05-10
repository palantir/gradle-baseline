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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;

public final class LazilyConfiguredMap<K, V> {
    private final Supplier<V> valueFactory;
    private final List<LazyValues<K, V>> values = new ArrayList<>();
    private final Map<K, Tracking<V>> computedValues = new HashMap<>();

    public LazilyConfiguredMap(Supplier<V> valueFactory) {
        this.valueFactory = valueFactory;
    }

    public void put(LazyValues<K, V> lazyValues) {
        values.add(lazyValues);
    }

    public void put(K key, Action<V> value) {
        put(requestedKey -> {
            if (requestedKey.equals(key)) {
                return Optional.of(value);
            }

            return Optional.empty();
        });
    }

    public void put(Provider<Map<K, Action<V>>> map) {
        put(key -> Optional.ofNullable(map.get().get(key)));
    }

    public V get(K key) {
        Optional<Tracking<V>> maybeExistingTracking = Optional.ofNullable(computedValues.get(key));
        Tracking<V> tracking = maybeExistingTracking.orElseGet(() -> new Tracking<>(valueFactory.get()));
        AtomicBoolean exists = new AtomicBoolean(maybeExistingTracking.isPresent());

        values.stream().skip(tracking.upTo).forEach(lazyValues -> {
            lazyValues.compute(key).ifPresent(action -> {
                exists.set(true);
                action.execute(tracking.value);
            });
        });

        if (!exists.get()) {
            throw new RuntimeException("can't find it");
        }

        tracking.upTo = values.size();
        computedValues.put(key, tracking);
        return tracking.value;
    }

    private static final class Tracking<V> {
        private int upTo = 0;
        private final V value;

        Tracking(V value) {
            this.value = value;
        }
    }

    public interface LazyValues<K, V> {
        Optional<Action<V>> compute(K key);
    }
}
