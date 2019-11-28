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

public final class AssertjPrimitiveEqualsWithDescription {

    @BeforeTemplate
    void bytes(byte actual, byte expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void shorts(short actual, short expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void ints(int actual, int expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void longs(long actual, long expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void floats(float actual, float expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void doubles(double actual, double expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void chars(char actual, char expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @BeforeTemplate
    void booleans(boolean actual, boolean expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual == expected).describedAs(description, descriptionArgs).isTrue();
    }

    @AfterTemplate
    @UseImportPolicy(ImportPolicy.STATIC_IMPORT_ALWAYS)
    <T> void after(T actual, T expected, String description, @Repeated Object descriptionArgs) {
        assertThat(actual).describedAs(description, descriptionArgs).isEqualTo(expected);
    }
}
