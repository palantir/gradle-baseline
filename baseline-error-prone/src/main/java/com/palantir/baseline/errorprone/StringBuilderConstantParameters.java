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
import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.SimpleTreeVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        name = "StringBuilderConstantParameters",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.WARNING,
        summary = "StringBuilder with a constant number of parameters should be replaced by simple concatenation")
public final class StringBuilderConstantParameters extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {
    private static final String MESSAGE =
            "StringBuilder with a constant number of parameters should be replaced by simple concatenation.\nThe Java "
                    + "compiler (jdk8) replaces concatenation of a constant number of arguments with a StringBuilder, "
                    + "while jdk 9+ take advantage of JEP 280 (https://openjdk.java.net/jeps/280) to efficiently "
                    + "pre-size the result for better performance than a StringBuilder.";

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STRING_BUILDER_TYPE_MATCHER = Matchers.isSameType(StringBuilder.class);
    private static final Matcher<ExpressionTree> STRING_BUILDER_TO_STRING = MethodMatchers.instanceMethod()
            .onExactClass(StringBuilder.class.getName())
            .named("toString")
            .withParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!STRING_BUILDER_TO_STRING.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        Optional<List<ExpressionTree>> result = tree.getMethodSelect().accept(StringBuilderVisitor.INSTANCE, state);
        if (!result.isPresent()) {
            return Description.NO_MATCH;
        }
        // Avoid rewriting code that removes comments.
        if (ASTHelpers.containsComments(tree, state)) {
            return buildDescription(tree).setMessage(MESSAGE).build();
        }
        List<ExpressionTree> arguments = result.get();
        Stream<String> prefixStream = arguments.stream()
                .findFirst()
                .map(ASTHelpers::getType)
                .filter(type -> ASTHelpers.isSameType(type, state.getTypeFromString("java.lang.String"), state))
                .map(ignored -> Stream.<String>empty())
                .orElseGet(() -> Stream.of("\"\""));

        return buildDescription(tree)
                .setMessage(MESSAGE)
                .addFix(SuggestedFix.builder()
                        .replace(
                                tree,
                                Streams.concat(prefixStream, arguments.stream().map(node ->
                                                getArgumentSourceString(state, node)))
                                        .collect(Collectors.joining(" + ")))
                        .build())
                .build();
    }

    private static String getArgumentSourceString(VisitorState state, ExpressionTree tree) {
        String originalSource = state.getSourceForNode(tree);
        // Ternary expressions must be parenthesized to avoid leaking into preceding or following expressions.
        if (tree instanceof ConditionalExpressionTree || tree instanceof BinaryTree) {
            return '(' + originalSource + ')';
        }
        return originalSource;
    }

    /**
     * {@link StringBuilderVisitor} checks if a {@link StringBuilder#toString()} invocation can be followed up a fluent
     * invocation chain, therefore must have a constant number of arguments. If so, the visitor results in a present
     * {@link Optional} of {@link ExpressionTree arguments} in the order they are {@link StringBuilder#append(Object)
     * appended}, otherwise an {@link Optional#empty() empty optional} is returned. This allows us to maintain a single
     * implementation for validation and building a {@link SuggestedFix} without sacrificing build time allocating
     * objects for {@link StringBuilder builders} which don't fit our pattern.
     */
    private static final class StringBuilderVisitor
            extends SimpleTreeVisitor<Optional<List<ExpressionTree>>, VisitorState> {
        private static final StringBuilderVisitor INSTANCE = new StringBuilderVisitor();

        private StringBuilderVisitor() {
            super(Optional.empty());
        }

        @Override
        public Optional<List<ExpressionTree>> visitNewClass(NewClassTree node, VisitorState state) {
            if (!STRING_BUILDER_TYPE_MATCHER.matches(node.getIdentifier(), state)) {
                return defaultAction(node, state);
            }
            if (node.getArguments().isEmpty()) {
                return Optional.of(new ArrayList<>());
            }
            if (node.getArguments().size() == 1
                    // We shouldn't replace pre-sized builders until we target java 11 across most libraries.
                    && (ASTHelpers.isSameType(
                                    ASTHelpers.getType(node.getArguments().get(0)),
                                    state.getTypeFromString("java.lang.String"),
                                    state)
                            || ASTHelpers.isSameType(
                                    ASTHelpers.getType(node.getArguments().get(0)),
                                    state.getTypeFromString("java.lang.CharSequence"),
                                    state))) {
                List<ExpressionTree> resultList = new ArrayList<>();
                resultList.add(node.getArguments().get(0));
                return Optional.of(resultList);
            }
            return Optional.empty();
        }

        @Override
        public Optional<List<ExpressionTree>> visitMemberSelect(MemberSelectTree node, VisitorState state) {
            if (node.getIdentifier().contentEquals("append")
                    || node.getIdentifier().contentEquals("toString")) {
                return node.getExpression().accept(this, state);
            }
            return defaultAction(node, state);
        }

        @Override
        public Optional<List<ExpressionTree>> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            Optional<List<ExpressionTree>> result = node.getMethodSelect().accept(this, state);
            if (result.isPresent()) {
                Preconditions.checkState(node.getArguments().size() == 1, "Expected a single argument to 'append'");
                result.get().add(node.getArguments().get(0));
            }
            return result;
        }
    }
}
