/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone;

import org.junit.jupiter.api.Test;

class OptionalOfNullableNonNullTest {

    @Test
    void testFix() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "  Object f() {",
                        "    return Optional.ofNullable(1);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "  Object f() {",
                        "    return Optional.of(1);",
                        "  }",
                        "}"
                )
                .doTest();
    }

    @Test
    void testFix_annotatedNonnull() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import javax.annotation.*;",
                        "class Test {",
                        "  Object f(@Nonnull Object obj) {",
                        "    return Optional.ofNullable(obj);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import javax.annotation.*;",
                        "class Test {",
                        "  Object f(@Nonnull Object obj) {",
                        "    return Optional.of(obj);",
                        "  }",
                        "}"
                )
                .doTest();
    }

    @Test
    void testFix_typeParameter() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "  Object f() {",
                        "    return Optional.<Integer>ofNullable(1);",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import java.util.*;",
                        "class Test {",
                        "  Object f() {",
                        "    return Optional.<Integer>of(1);",
                        "  }",
                        "}"
                )
                .doTest();
    }

    @Test
    void testNegative() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import java.util.*;",
                        "import java.util.function.*;",
                        "import javax.annotation.*;",
                        "class Test {",
                        "  void f(Consumer<Object> sink, Map<String, Object> map, Object p0, @Nullable Object p1) {",
                        "    sink.accept(Optional.ofNullable(p0));",
                        "    sink.accept(Optional.ofNullable(p1));",
                        "    sink.accept(Optional.ofNullable(map.get(\"a\")).orElse(\"b\"));",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator fix() {
        return RefactoringValidator.of(new OptionalOfNullableNonNull(), getClass());
    }
}
