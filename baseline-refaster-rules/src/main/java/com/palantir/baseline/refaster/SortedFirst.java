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
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/** Example refaster rule as shown at https://errorprone.info/docs/refaster. */
public final class SortedFirst<T> {

    @BeforeTemplate
    Optional<T> before(Stream<T> stream, Comparator<? super T> comparator) {
        return stream.sorted(comparator).findFirst();
    }

    @AfterTemplate
    Optional<T> after(Stream<T> stream, Comparator<? super T> comparator) {
        return stream.min(comparator);
    }
}
