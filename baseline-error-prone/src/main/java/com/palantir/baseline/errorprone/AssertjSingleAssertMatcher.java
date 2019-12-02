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

import com.google.errorprone.VisitorState;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.lang.model.type.TypeKind;


final class AssertjSingleAssertMatcher {

    private static final Matcher<ExpressionTree> ASSERT_THAT = MethodMatchers.staticMethod()
            .onClass("org.assertj.core.api.Assertions")
            .named("assertThat");

    private static final Matcher<ExpressionTree> ASSERTION = MethodMatchers.instanceMethod()
            .onDescendantOf("org.assertj.core.api.Assert")
            .withAnyName();

    // Matches metadata methods which only impact messages.
    private static final Matcher<ExpressionTree> METADATA_METHOD = Matchers.anyOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf("org.assertj.core.api.Descriptable")
                    .namedAnyOf("as", "describedAs"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf("org.assertj.core.api.Assert")
                    .namedAnyOf("withRepresentation", "withThreadDumpOnError"),
            MethodMatchers.instanceMethod()
                    .onDescendantOf("org.assertj.core.api.AbstractAssert")
                    .namedAnyOf("overridingErrorMessage", "withFailMessage"));

    private static final Matcher<Tree> SINGLE_STATEMENT = Matchers.parentNode(Matchers.anyOf(
            Matchers.kindIs(Tree.Kind.EXPRESSION_STATEMENT),
            // lambda returning void
            (tree, state) -> tree instanceof LambdaExpressionTree
                    && state.getTypes().findDescriptorType(ASTHelpers.getType(tree))
                    .getReturnType().getKind() == TypeKind.VOID));

    private final BiFunction<SingleAssertMatch, VisitorState, Description> function;

    static AssertjSingleAssertMatcher of(BiFunction<SingleAssertMatch, VisitorState, Description> function) {
        return new AssertjSingleAssertMatcher(function);
    }

    private AssertjSingleAssertMatcher(BiFunction<SingleAssertMatch, VisitorState, Description> function) {
        this.function = function;
    }

    public Description matches(ExpressionTree tree, VisitorState state) {
        // Only match full statements, otherwise dangling statements may expect the wrong type.
        if (!SINGLE_STATEMENT.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        return matchAssertj(tree, state)
                .flatMap(list -> list.size() == 2
                        ? Optional.of(new SingleAssertMatch(list.get(0), list.get(1)))
                        : Optional.empty())
                .map(result -> function.apply(result, state))
                .orElse(Description.NO_MATCH);
    }

    private static Optional<List<MethodInvocationTree>> matchAssertj(
            ExpressionTree expressionTree, VisitorState state) {
        if (expressionTree == null) {
            return Optional.empty();
        }
        if (expressionTree instanceof MemberSelectTree) {
            return matchAssertj(((MemberSelectTree) expressionTree).getExpression(), state);
        }
        if (expressionTree instanceof MethodInvocationTree) {
            MethodInvocationTree methodInvocationTree = (MethodInvocationTree) expressionTree;
            if (ASSERT_THAT.matches(methodInvocationTree, state)
                    && methodInvocationTree.getArguments().size() == 1) {
                List<MethodInvocationTree> results = new ArrayList<>();
                results.add(methodInvocationTree);
                return Optional.of(results);
            } else if (METADATA_METHOD.matches(methodInvocationTree, state)) {
                return matchAssertj(methodInvocationTree.getMethodSelect(), state);
            } else if (ASSERTION.matches(methodInvocationTree, state)) {
                Optional<List<MethodInvocationTree>> result =
                        matchAssertj(methodInvocationTree.getMethodSelect(), state);
                result.ifPresent(list -> list.add(methodInvocationTree));
                return result;
            }
        }
        return Optional.empty();
    }

    static final class SingleAssertMatch {
        private final MethodInvocationTree assertThat;
        private final MethodInvocationTree check;

        SingleAssertMatch(MethodInvocationTree assertThat, MethodInvocationTree check) {
            this.assertThat = assertThat;
            this.check = check;
        }

        MethodInvocationTree getAssertThat() {
            return assertThat;
        }

        MethodInvocationTree getCheck() {
            return check;
        }
    }
}
