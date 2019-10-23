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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.errorprone.refaster.ImportPolicy;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.Repeated;
import com.google.errorprone.refaster.annotation.UseImportPolicy;
import java.util.Optional;

public final class AssertjOptionalIsNotPresentWithDescription<T> {

    @BeforeTemplate
    void before1(Optional<T> thing, String description, @Repeated Object descriptionArgs) {
        assertThat(thing.isPresent()).describedAs(description, descriptionArgs).isFalse();
    }

    @BeforeTemplate
    void before2(Optional<T> thing, String description, @Repeated Object descriptionArgs) {
        assertThat(!thing.isPresent()).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void before3(Optional<T> thing, String description, @Repeated Object descriptionArgs) {
        assertThat(thing).describedAs(description, descriptionArgs).isEqualTo(Optional.empty());
    }

    @BeforeTemplate
    void before4(Optional<T> thing, String description, @Repeated Object descriptionArgs) {
        assertThat(Optional.empty()).describedAs(description, descriptionArgs).isEqualTo(thing);
    }

    @AfterTemplate
    @UseImportPolicy(ImportPolicy.STATIC_IMPORT_ALWAYS)
    void after(Optional<T> thing, String description, @Repeated Object descriptionArgs) {
        assertThat(thing).describedAs(description, descriptionArgs).isNotPresent();
    }
}
