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

import com.google.errorprone.refaster.ImportPolicy;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.UseImportPolicy;
import org.assertj.core.api.IterableAssert;
import org.assertj.core.api.ListAssert;


public final class AssertjCollectionHasSameSizeAsArray<T, U> {

    @BeforeTemplate
    IterableAssert<T> before(IterableAssert<T> assertInProgress, U[] expected) {
        return assertInProgress.hasSize(expected.length);
    }

    @BeforeTemplate
    ListAssert<T> before(ListAssert<T> assertInProgress, U[] expected) {
        return assertInProgress.hasSize(expected.length);
    }

    @AfterTemplate
    @UseImportPolicy(ImportPolicy.STATIC_IMPORT_ALWAYS)
    IterableAssert<T> after(IterableAssert<T> assertInProgress, U[] expected) {
        return assertInProgress.hasSameSizeAs(expected);
    }
}
