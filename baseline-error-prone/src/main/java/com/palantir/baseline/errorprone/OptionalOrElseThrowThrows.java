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

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.predicates.TypePredicates;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.util.SimpleTreeVisitor;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Supplier;

@AutoService(BugChecker.class)
@BugPattern(
        name = "OptionalOrElseThrowThrows",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "orElseThrow argument must return an exception, not throw one")
public final class OptionalOrElseThrowThrows extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> OR_ELSE_THROW_METHOD = MethodMatchers.instanceMethod()
            .onClass(TypePredicates.isExactTypeAny(ImmutableList.of(
                    Optional.class.getName(),
                    OptionalDouble.class.getName(),
                    OptionalInt.class.getName(),
                    OptionalLong.class.getName())))
            .named("orElseThrow")
            .withParameters(Supplier.class.getName());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!OR_ELSE_THROW_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        ExpressionTree argument = Iterables.getOnlyElement(tree.getArguments());
        if (!(argument instanceof LambdaExpressionTree)) {
            return Description.NO_MATCH;
        }
        LambdaExpressionTree lambdaArgument = (LambdaExpressionTree) argument;
        if (!lambdaArgument.getBody().accept(ThrowsPredicateVisitor.INSTANCE, null)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage("orElseThrow argument must return an exception, not throw one")
                .build();
    }

    private static final class ThrowsPredicateVisitor extends SimpleTreeVisitor<Boolean, Void> {
        static final TreeVisitor<Boolean, Void> INSTANCE = new ThrowsPredicateVisitor();

        private ThrowsPredicateVisitor() {
            super(false);
        }

        @Override
        public Boolean visitThrow(ThrowTree node, Void state) {
            return true;
        }

        @Override
        public Boolean visitBlock(BlockTree node, Void state) {
            // Only validate the first statement for the most common case to avoid unnecessary complexity
            StatementTree firstStatement = Iterables.getFirst(node.getStatements(), null);
            return firstStatement != null && firstStatement.accept(this, state);
        }
    }
}
