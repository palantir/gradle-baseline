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
import com.google.errorprone.refaster.annotation.Repeated;
import com.google.errorprone.refaster.annotation.UseImportPolicy;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public final class AssertjOptionalHasValueRedundantWithDescription<T> {

    @BeforeTemplate
    void redundantAssertion(
            Optional<T> optional,
            T innerValue,
            String description1,
            @Repeated Object descriptionArgs1,
            String description2,
            @Repeated Object descriptionArgs2) {
        assertThat(optional).describedAs(description1, descriptionArgs1).isPresent();
        assertThat(optional).describedAs(description2, descriptionArgs2).hasValue(innerValue);
    }

    @AfterTemplate
    @UseImportPolicy(ImportPolicy.STATIC_IMPORT_ALWAYS)
    void after(
            Optional<T> optional,
            T innerValue,
            // The first assertion is unnecessary
            String _description1,
            @Repeated Object _descriptionArgs1,
            String description2,
            @Repeated Object descriptionArgs2) {
        assertThat(optional).describedAs(description2, descriptionArgs2).hasValue(innerValue);
    }
}
