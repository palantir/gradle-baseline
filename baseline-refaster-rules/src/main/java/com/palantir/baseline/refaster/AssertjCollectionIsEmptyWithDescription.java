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
import java.util.Collection;

public final class AssertjCollectionIsEmptyWithDescription<T> {

    @BeforeTemplate
    void bad1(Collection<T> things, String description, @Repeated Object descriptionArgs) {
        assertThat(things.size() == 0).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void bad2(Collection<T> things, String description, @Repeated Object descriptionArgs) {
        assertThat(things.isEmpty()).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void bad3(Collection<T> things, String description, @Repeated Object descriptionArgs) {
        assertThat(things.size()).describedAs(description, descriptionArgs).isZero();
    }

    @BeforeTemplate
    void bad4(Collection<T> things, String description, @Repeated Object descriptionArgs) {
        assertThat(things.size()).describedAs(description, descriptionArgs).isEqualTo(0);
    }

    @AfterTemplate
    @UseImportPolicy(ImportPolicy.STATIC_IMPORT_ALWAYS)
    void after(Iterable<T> things, String description, @Repeated Object descriptionArgs) {
        assertThat(things).describedAs(description, descriptionArgs).isEmpty();
    }
}
