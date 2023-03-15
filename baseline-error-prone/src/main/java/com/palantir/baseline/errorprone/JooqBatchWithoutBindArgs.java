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

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.BugPattern.StandardTags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import java.util.Collection;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        tags = StandardTags.PERFORMANCE,
        summary = "jOOQ batch methods that execute without bind args can cause performance problems.",
        explanation =
                "When batch queries execute without bind args, each query is sent to the database as a string with all"
                    + " variables inline. Inline variables cause each query in the batch to be unique, so the database"
                    + " uses extra CPU and memory to parse and query plan each query. Instead use one of the other"
                    + " jOOQ batch methods that is documented as executing queries with bind args, as this allows"
                    + " parsing and query planning the query once and then executing any number of times with"
                    + " different bind values.")
public final class JooqBatchWithoutBindArgs extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final String DSL_CONTEXT = "org.jooq.DSLContext";
    private static final String BATCH = "batch";

    private static final Supplier<Type> QUERY_TYPE =
            VisitorState.memoize(state -> state.getTypeFromString("org.jooq.Query"));

    private static final Matcher<ExpressionTree> BATCH_WITHOUT_BINDS_MATCHER = Matchers.anyOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(DSL_CONTEXT)
                    .named(BATCH)
                    .withParameters("org.jooq.Queries"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(DSL_CONTEXT)
                    .named(BATCH)
                    .withParameters(Collection.class.getName()),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(DSL_CONTEXT)
                    .named(BATCH)
                    .withParametersOfType(ImmutableList.of(Suppliers.arrayOf(Suppliers.STRING_TYPE))),
            MethodMatchers.instanceMethod()
                    .onDescendantOf(DSL_CONTEXT)
                    .named(BATCH)
                    .withParametersOfType(ImmutableList.of(Suppliers.arrayOf(QUERY_TYPE))));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (BATCH_WITHOUT_BINDS_MATCHER.matches(tree, state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }
}
