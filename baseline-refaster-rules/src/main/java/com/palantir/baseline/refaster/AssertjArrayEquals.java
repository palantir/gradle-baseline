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
import java.util.Arrays;

public final class AssertjArrayEquals {

    @BeforeTemplate
    void bytes(byte[] actual, byte[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void shorts(short[] actual, short[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void ints(int[] actual, int[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void longs(long[] actual, long[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void floats(float[] actual, float[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void doubles(double[] actual, double[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void chars(char[] actual, char[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    void booleans(boolean[] actual, boolean[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @BeforeTemplate
    <T> void arbitraryObjects(T[] actual, T[] expected) {
        assertThat(Arrays.equals(actual, expected)).isTrue();
    }

    @AfterTemplate
    @UseImportPolicy(ImportPolicy.STATIC_IMPORT_ALWAYS)
    <T> void after(T[] actual, T[] expected) {
        assertThat(actual).isEqualTo(expected);
    }
}
