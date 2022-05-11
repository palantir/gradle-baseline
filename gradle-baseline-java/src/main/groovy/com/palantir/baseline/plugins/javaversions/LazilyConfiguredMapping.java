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

public final class LazilyConfiguredMapping<K, V, A> {
    private final Supplier<V> valueFactory;
    private final List<LazyValues<K, V, A>> values = new ArrayList<>();
    private final Map<K, Optional<V>> computedValues = new HashMap<>();
    private boolean finalized = false;

    public LazilyConfiguredMapping(Supplier<V> valueFactory) {
        this.valueFactory = valueFactory;
    }

    public void put(LazyValues<K, V, A> lazyValues) {
        ensureNotFinalized();

        values.add(lazyValues);
    }

    public void put(K key, Action<V> value) {
        ensureNotFinalized();

        put((requestedKey, _ignored) -> {
            if (requestedKey.equals(key)) {
                return Optional.of(value);
            }

            return Optional.empty();
        });
    }

    private void ensureNotFinalized() {
        if (finalized) {
            throw new IllegalStateException(String.format(
                    "This %s has already been finalized as get() hase been called. "
                            + "No further elements can be added to it",
                    LazilyConfiguredMapping.class.getSimpleName()));
        }
    }

    public Optional<V> get(K key, A additionalData) {
        finalized = true;

        return computedValues.computeIfAbsent(key, _ignored -> {
            V value = valueFactory.get();
            AtomicBoolean created = new AtomicBoolean(false);
            values.forEach(lazyValues -> {
                lazyValues.compute(key, additionalData).ifPresent(action -> {
                    created.set(true);
                    action.execute(value);
                });
            });

            if (created.get()) {
                return Optional.of(value);
            }

            return Optional.empty();
        });
    }

    public interface LazyValues<K, V, A> {
        Optional<Action<V>> compute(K key, A additionalData);
    }
}
