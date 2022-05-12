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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LazilyConfiguredMappingTest {
    private final LazilyConfiguredMapping<String, Extension, Character> lazilyConfiguredMapping =
            new LazilyConfiguredMapping<>(() -> new Extension(0));

    @Test
    void empty_mapping_returns_optional_empty() {
        assertThat(lazilyConfiguredMapping.get("abc", 'a')).isEmpty();
    }

    @Test
    void can_put_a_value_and_get_it_out_again() {
        lazilyConfiguredMapping.put("foo", extension -> extension.number = 4);

        assertThat(lazilyConfiguredMapping.get("foo", 'a')).hasValue(new Extension(4));
    }

    @Test
    void can_put_a_lazy_value_in_and_get_it_out_again() {
        lazilyConfiguredMapping.put((key, additionalData) -> {
            assertThat(additionalData).isEqualTo('b');
            return Optional.of(extension -> extension.number = Integer.parseInt(key));
        });

        assertThat(lazilyConfiguredMapping.get("3", 'b')).hasValue(new Extension(3));
        assertThat(lazilyConfiguredMapping.get("9", 'b')).hasValue(new Extension(9));
    }

    @Test
    void lazy_values_are_able_to_not_return_values() {
        lazilyConfiguredMapping.put((_key, _additionalData) -> Optional.empty());

        assertThat(lazilyConfiguredMapping.get("abc", 'a')).isEmpty();
    }

    @Test
    void interspersing_putting_values_takes_the_last_set_value() {
        lazilyConfiguredMapping.put("1", extension -> extension.number = 80);
        lazilyConfiguredMapping.put(
                (key, _ignored) -> Optional.of(extension -> extension.number = Integer.parseInt(key)));
        lazilyConfiguredMapping.put("4", extension -> extension.number = 99);

        assertThat(lazilyConfiguredMapping.get("1", 'c')).hasValue(new Extension(1));
        assertThat(lazilyConfiguredMapping.get("3", 'c')).hasValue(new Extension(3));
        assertThat(lazilyConfiguredMapping.get("4", 'c')).hasValue(new Extension(99));
    }

    @Test
    void throws_if_putting_values_after_being_finalized() {
        lazilyConfiguredMapping.get("abc", 'c');

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            lazilyConfiguredMapping.put("foo", extension -> {});
        });

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            lazilyConfiguredMapping.put((_key, _additionalData) -> Optional.of(extension -> {}));
        });
    }

    private static final class Extension {
        public int number;

        Extension(int number) {
            this.number = number;
        }

        @Override
        public boolean equals(Object obj) {
            return number == ((Extension) obj).number;
        }

        @Override
        public int hashCode() {
            return number;
        }

        @Override
        public String toString() {
            return Integer.toString(number);
        }
    }
}
