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

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public final class JooqResultStreamLeakTest {

    private final CompilationTestHelper testHelper =
            CompilationTestHelper.newInstance(JooqResultStreamLeak.class, getClass());

    private final RefactoringValidator refactoringValidator =
            RefactoringValidator.of(new JooqResultStreamLeak(), getClass());

    @Test
    public void test_positive() {
        testHelper
                .addSourceLines(
                        "JooqStream.java",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "import org.jooq.Record;",
                        "import org.jooq.ResultQuery;",
                        "class Test {",
                        "  void f(ResultQuery<Record> rq) {",
                        "    // BUG: Diagnostic contains: should be closed",
                        "    rq.stream().map(r -> r.getValue(0));",
                        "    // BUG: Diagnostic contains: should be closed",
                        "    rq.fetchStream().map(r -> r.getValue(0));",
                        "    // BUG: Diagnostic contains: should be closed",
                        "    rq.fetchLazy();",
                        "    try (Stream<String> stream = rq.stream().collect(Collectors.toList()).stream()) {",
                        "      stream.collect(Collectors.joining(\", \"));",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_query_steps_ignored() {
        testHelper
                .addSourceLines(
                        "JooqStream.java",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "import org.jooq.Record;",
                        "import org.jooq.SelectConditionStep;",
                        "class Test {",
                        "  SelectConditionStep<Record> f(SelectConditionStep<Record> select) {",
                        "    return select.and(\"some_field <> 3\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_negative() {
        testHelper
                .addSourceLines(
                        "JooqStream.java",
                        "import java.util.stream.Stream;",
                        "import org.jooq.Record;",
                        "import org.jooq.ResultQuery;",
                        "class Test {",
                        "  void f(ResultQuery rq) {",
                        "    try (Stream<Record> stream = rq.stream()) {",
                        "      Stream<Field<?>> newStream = stream.map(r -> r.getValue(0));",
                        "    }",
                        "    try (Stream<Record> stream = rq.fetchStream()) {",
                        "      Stream<Field<?>> newStream = stream.map(r -> r.getValue(0));",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_fix() {
        refactoringValidator
                .addInputLines(
                        "in/JooqStream.java",
                        "import java.util.stream.Stream;",
                        "import org.jooq.Record;",
                        "import org.jooq.ResultQuery;",
                        "class Test {",
                        "  void f(ResultQuery<Record> rq) {",
                        "    rq.stream().map(r -> r.getValue(0));",
                        "  }",
                        "}")
                .addOutputLines(
                        "out/JooqStream.java",
                        "import java.util.stream.Stream;",
                        "import org.jooq.Record;",
                        "import org.jooq.ResultQuery;",
                        "class Test {",
                        "  void f(ResultQuery<Record> rq) {",
                        "    try (Stream<Record> stream = rq.stream()) {",
                        "      stream.map(r -> r.getValue(0));",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void test_fix_variable() {
        refactoringValidator
                .addInputLines(
                        "in/JooqStream.java",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "import org.jooq.Record;",
                        "import org.jooq.ResultQuery;",
                        "class Test {",
                        "  void f(ResultQuery<Record> rq) {",
                        "    String res = rq.stream().map(r -> r.toString()).collect(Collectors.joining(\", \"));",
                        "  }",
                        "}")
                .addOutputLines(
                        "out/JooqStream.java",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "import org.jooq.Record;",
                        "import org.jooq.ResultQuery;",
                        "class Test {",
                        "  void f(ResultQuery<Record> rq) {",
                        "    String res;",
                        "    try (Stream<Record> stream = rq.stream()) {",
                        "      res = stream.map(r -> r.toString()).collect(Collectors.joining(\", \"));",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }
}
