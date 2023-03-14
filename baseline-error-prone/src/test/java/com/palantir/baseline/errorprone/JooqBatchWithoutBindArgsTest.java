/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.Test;

/**
 * See {@code org.jooq.DSLContext#batch} docs to see which ones use bind args.
 */
public final class JooqBatchWithoutBindArgsTest {

    private void testFail(String batchArgs) {
        test(batchArgs, true);
    }

    private void testPass(String batchArgs) {
        test(batchArgs, false);
    }

    private void test(String batchArgs, boolean fail) {
        CompilationTestHelper.newInstance(JooqBatchWithoutBindArgs.class, getClass())
                .addSourceLines(
                        "Test.java",
                        "import org.jooq.DSLContext;",
                        "import org.jooq.Queries;",
                        "import org.jooq.Query;",
                        "import org.jooq.Table;",
                        "import org.jooq.Record;",
                        "import org.jooq.Field;",
                        "import org.jooq.impl.DSL;",
                        "import java.util.ArrayList;",
                        "",
                        "class Test {",
                        "",
                        "  static final ArrayList<Query> QUERY_LIST = new ArrayList<>();",
                        "  static final Queries QUERIES = DSL.queries(QUERY_LIST);",
                        "",
                        "  void f(DSLContext ctx, Table<? extends Record> table, Field<Integer> intField) {",
                        fail ? "    // BUG: Diagnostic contains: jOOQ batch methods that execute without bind" : "",
                        "    ctx.batch(" + batchArgs + ").execute();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    public void testPassSingleQueryString() {
        // batch(String)
        testPass("\"DELETE FROM table WHERE id = ?\"");
    }

    @Test
    public void testFailStringArray() {
        // batch(String...)
        testFail("\"DELETE FROM table WHERE id = 1\", \"DELETE FROM table WHERE id = 2\"");
    }

    @Test
    public void testPassStringWithBindsArray() {
        // batch(String, Object[]...)
        testPass("\"DELETE FROM table WHERE id = ?\", new Object[][]{{1}, {2}, {3}}");
    }

    @Test
    public void testFailQueryList() {
        // batch(Collection<? extends Query>)
        testFail("QUERY_LIST");
    }

    @Test
    public void testFailQueries() {
        // batch(Queries)
        testFail("QUERIES");
    }

    @Test
    public void testPassSingleQuery() {
        // batch(Query)
        testPass("ctx.deleteFrom(table).where(intField.eq((Integer) null))");
    }

    @Test
    public void testFailQueryArray() {
        // batch(Query...)
        testFail("ctx.deleteFrom(table).where(intField.eq(1)), ctx.selectFrom(table).where(intField.eq(2))");
    }

    @Test
    public void testPassQueryWithBindsArray() {
        // batch(Query, Object[]...)
        testPass("ctx.deleteFrom(table).where(intField.eq((Integer) null)), new Object[][]{{1}, {2}, {3}}");
    }
}
