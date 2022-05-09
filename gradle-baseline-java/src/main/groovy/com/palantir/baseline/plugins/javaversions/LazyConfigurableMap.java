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

public final class LazyConfigurableMap<K, V> {
    private final Supplier<V> valueFactory;
    private final List<LazyValues<K, V>> values = new ArrayList<>();
    private final Map<K, Lol<V>> computedValues = new HashMap<>();

    public LazyConfigurableMap(Supplier<V> valueFactory) {
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
        Optional<Lol<V>> existing = Optional.ofNullable(computedValues.get(key));
        Lol<V> lol = existing.orElseGet(() -> new Lol<>(0, valueFactory.get()));
        AtomicBoolean exists = new AtomicBoolean(existing.isPresent());

        values.stream().skip(lol.upTo).forEach(lazyValues -> {
            lazyValues.blah(key).ifPresent(action -> {
                exists.set(true);
                action.execute(lol.value);
            });
        });

        if (exists.get()) {
            lol.upTo = values.size();
            computedValues.put(key, lol);
            return lol.value;
        }

        throw new RuntimeException("can't find it");

        //        computedValues.computeIfAbsent(key, _ignored -> {
        //            V value = valueFactory.get();
        //            values.forEach(lazyValues -> {
        //                lazyValues.blah(key).ifPresent(action -> {
        //                    exists.set(true);
        //                    action.execute(value);
        //                });
        //            });
        //
        //            if (exists.get()) {}
        //
        //            for (int i = values.size() - 1; i >= 0; i--) {
        //                values.get(i).blah(key).ifPresent(action -> action.execute(value));
        //            }
        //            return value;
        //        });
        //
        //        return computedValues.computeIfAbsent(key, _ignored -> {
        //            V value = valueFactory.get();
        //            for (int i = values.size() - 1; i >= 0; i--) {
        //                values.get(i).blah(key).ifPresent(action -> action.execute(value));
        //            }
        //            return value;
        //        });
    }

    private static final class Lol<V> {
        private int upTo;
        private final V value;

        Lol(int upTo, V value) {
            this.upTo = upTo;
            this.value = value;
        }
    }

    public interface LazyValues<K, V> {
        Optional<Action<V>> blah(K key);
    }
}
