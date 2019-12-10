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
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "AssertjPrimitiveComparison",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Prefer using AssertJ fluent comparisons over logic in an assertThat statement for better "
                + "failure output. assertThat(a == b).isTrue() failures report 'expected true' where "
                + "assertThat(a).isEqualTo(b) provides the expected and actual values.")
public final class AssertjPrimitiveComparison extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> IS_TRUE = MethodMatchers.instanceMethod()
            .onDescendantOf("org.assertj.core.api.Assert")
            .named("isTrue")
            .withParameters();

    private static final Matcher<ExpressionTree> IS_FALSE = MethodMatchers.instanceMethod()
            .onDescendantOf("org.assertj.core.api.Assert")
            .named("isFalse")
            .withParameters();

    private static final Matcher<ExpressionTree> BOOLEAN_ASSERT = Matchers.anyOf(IS_TRUE, IS_FALSE);

    private final AssertjSingleAssertMatcher matcher = AssertjSingleAssertMatcher.of(this::match);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (BOOLEAN_ASSERT.matches(tree, state)) {
            return matcher.matches(tree, state);
        }
        return Description.NO_MATCH;
    }

    private Description match(AssertjSingleAssertMatcher.SingleAssertMatch match, VisitorState state) {
        boolean negated = IS_FALSE.matches(match.getCheck(), state);
        if (!negated && !IS_TRUE.matches(match.getCheck(), state)) {
            return Description.NO_MATCH;
        }
        ExpressionTree target = match.getAssertThat().getArguments().get(0);
        if (!(target instanceof BinaryTree)) {
            return Description.NO_MATCH;
        }
        BinaryTree binaryTree = (BinaryTree) target;
        Optional<Type> maybeTarget = getPromotionType(binaryTree.getLeftOperand(), binaryTree.getRightOperand(), state);
        if (!maybeTarget.isPresent()) {
            return Description.NO_MATCH;
        }
        Type targetType = maybeTarget.get();
        Optional<String> comparison = negated
                ? negate(binaryTree.getKind()).flatMap(AssertjPrimitiveComparison::getAssertionName)
                : getAssertionName(binaryTree.getKind());
        if (!comparison.isPresent()) {
            return Description.NO_MATCH;
        }
        ExpressionTree expected = binaryTree.getRightOperand();
        ExpressionTree actual = binaryTree.getLeftOperand();
        SuggestedFix fix = SuggestedFix.builder()
                .replace(
                        state.getEndPosition(((MemberSelectTree) match.getCheck().getMethodSelect()).getExpression()),
                        state.getEndPosition(match.getCheck()),
                        String.format(".%s(%s)", comparison.get(),
                                getExpressionSource(expected, targetType, state, false)))
                .replace(target, getExpressionSource(actual, targetType, state, true))
                .build();
        return buildDescription(match.getAssertThat())
                .addFix(fix)
                .build();
    }

    private static String getExpressionSource(
            ExpressionTree expression,
            Type targetType,
            VisitorState state,
            // True if an exact type match is required, otherwise assignment compatibility is allowed.
            boolean strict) {
        String source = state.getSourceForNode(expression);
        Types types = state.getTypes();
        Type resultType = types.unboxedTypeOrType(ASTHelpers.getType(expression));
        if (strict ? types.isSameType(resultType, targetType) : types.isAssignable(resultType, targetType)) {
            return source;
        }
        String cast = '(' + MoreSuggestedFixes.prettyType(state, null, targetType) + ") ";
        if (ASTHelpers.requiresParentheses(expression, state)) {
            return cast + '(' + source + ')';
        }
        return cast + source;
    }

    /**
     * Returns the promotion type used to compare the results of the first and second args based on
     * <a href=https://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.6.2">jls-5.6.2</a>.
     */
    private static Optional<Type> getPromotionType(
            ExpressionTree firstArg,
            ExpressionTree secondArg,
            VisitorState state) {
        Types types = state.getTypes();
        // Handle unboxing per JLS 5.6.2 item 1.
        Type firstType = types.unboxedTypeOrType(ASTHelpers.getType(firstArg));
        Type secondType = types.unboxedTypeOrType(ASTHelpers.getType(secondArg));
        if (!firstType.isPrimitive() || !secondType.isPrimitive()) {
            return Optional.empty();
        }
        if (types.isSameType(firstType, secondType)) {
            return Optional.of(firstType);
        }
        // Handle widening per JLS 5.6.2 item 2.
        Symtab symtab = state.getSymtab();
        // If either operand is of type double, the other is converted to double.
        if (types.isSameType(symtab.doubleType, firstType) || types.isSameType(symtab.doubleType, secondType)) {
            return Optional.of(symtab.doubleType);
        }
        // Otherwise, if either operand is of type float, the other is converted to float.
        if (types.isSameType(symtab.floatType, firstType) || types.isSameType(symtab.floatType, secondType)) {
            return Optional.of(symtab.floatType);
        }
        // Otherwise, if either operand is of type long, the other is converted to long.
        if (types.isSameType(symtab.longType, firstType) || types.isSameType(symtab.longType, secondType)) {
            return Optional.of(symtab.longType);
        }
        // Otherwise, both operands are converted to type int.
        return Optional.of(symtab.intType);
    }

    @SuppressWarnings("SwitchStatementDefaultCase")
    private static Optional<String> getAssertionName(Tree.Kind binaryExpression) {
        switch (binaryExpression) {
            case EQUAL_TO:
                return Optional.of("isEqualTo");
            case NOT_EQUAL_TO:
                return Optional.of("isNotEqualTo");
            case LESS_THAN:
                return Optional.of("isLessThan");
            case LESS_THAN_EQUAL:
                return Optional.of("isLessThanOrEqualTo");
            case GREATER_THAN:
                return Optional.of("isGreaterThan");
            case GREATER_THAN_EQUAL:
                return Optional.of("isGreaterThanOrEqualTo");
            default:
                return Optional.empty();
        }
    }

    @SuppressWarnings("SwitchStatementDefaultCase")
    private static Optional<Tree.Kind> negate(Tree.Kind binaryExpression) {
        switch (binaryExpression) {
            case EQUAL_TO:
                return Optional.of(Tree.Kind.NOT_EQUAL_TO);
            case NOT_EQUAL_TO:
                return Optional.of(Tree.Kind.EQUAL_TO);
            case LESS_THAN:
                return Optional.of(Tree.Kind.GREATER_THAN_EQUAL);
            case LESS_THAN_EQUAL:
                return Optional.of(Tree.Kind.GREATER_THAN);
            case GREATER_THAN:
                return Optional.of(Tree.Kind.LESS_THAN_EQUAL);
            case GREATER_THAN_EQUAL:
                return Optional.of(Tree.Kind.LESS_THAN);
            default:
                return Optional.empty();
        }
    }
}
