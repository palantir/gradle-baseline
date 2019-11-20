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
import java.util.function.Supplier;

/** No reason to redefine a supplier when the target type is also a supplier. */
final class SupplierRedefinition<T> {

    @BeforeTemplate
    @SuppressWarnings({"Convert2MethodRef", "FunctionalExpressionCanBeFolded"})
    Supplier<T> before1(Supplier<T> input) {
        return () -> input.get();
    }

    @BeforeTemplate
    @SuppressWarnings("FunctionalExpressionCanBeFolded")
    Supplier<T> before2(Supplier<T> input) {
        return input::get;
    }

    @AfterTemplate
    Supplier<T> after(Supplier<T> input) {
        return input;
    }
}
