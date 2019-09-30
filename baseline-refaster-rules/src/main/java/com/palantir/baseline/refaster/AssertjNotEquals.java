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
import com.google.errorprone.refaster.annotation.UseImportPolicy;
import java.util.Objects;

/**
 * We have to guess as to which value is expected and which is the actual result, but either way failures will
 * produce significantly more helpful output.
 */
public final class AssertjNotEquals<T> {

    @BeforeTemplate
    void before1(T expected, T actual) {
        assertThat(!actual.equals(expected)).isTrue();
    }

    @BeforeTemplate
    void before2(T expected, T actual) {
        assertThat(!Objects.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void before3(T expected, T actual) {
        assertThat(actual.equals(expected)).isFalse();
    }

    @BeforeTemplate
    void before4(T expected, T actual) {
        assertThat(Objects.equals(actual, expected)).isFalse();
    }

    @AfterTemplate
    @UseImportPolicy(ImportPolicy.STATIC_IMPORT_ALWAYS)
    void after(T expected, T actual) {
        assertThat(actual).isNotEqualTo(expected);
    }
}
