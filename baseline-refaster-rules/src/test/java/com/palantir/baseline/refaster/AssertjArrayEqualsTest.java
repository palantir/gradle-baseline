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

import org.junit.Test;

public class AssertjArrayEqualsTest {
    @Test
    public void sanity_check() {
        byte[] bytes = {1, 2, 3};
        // both of these work, but I think isEqualTo reads a bit more nicely
        assertThat(bytes).isEqualTo(new byte[]{1, 2, 3});
        assertThat(bytes).containsExactly(new byte[]{1, 2, 3});
    }

    @Test
    public void bytes() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(byte[] a, byte[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(byte[] a, byte[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void shorts() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(short[] a, short[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(short[] a, short[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void ints() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(int[] a, int[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(int[] a, int[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void longs() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(long[] a, long[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(long[] a, long[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void floats() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(float[] a, float[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(float[] a, float[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void doubles() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(double[] a, double[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(double[] a, double[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void chars() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(char[] a, char[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(char[] a, char[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void booleans() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(boolean[] a, boolean[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(boolean[] a, boolean[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }

    @Test
    public void strings() {
        RefasterTestHelper
                .forRefactoring(AssertjArrayEquals.class)
                .withInputLines(
                        "Test",
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(String[] a, String[] b) { assertThat(Arrays.equals(a,b)).isTrue(); }",
                        "}")
                .hasOutputLines(
                        "import static org.assertj.core.api.Assertions.assertThat;",
                        "import java.util.Arrays;",
                        "public class Test {",
                        "  void f(String[] a, String[] b) { assertThat(a).isEqualTo(b); }",
                        "}");
    }
}
