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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.tree.JCTree;
import java.util.Collection;
import java.util.Optional;
import org.immutables.value.Value.Immutable;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "Use the isEmpty method instead of checking collection size")
public final class CardinalityEqualsZero extends BugChecker implements BugChecker.BinaryTreeMatcher {
    private static final Matcher<ExpressionTree> COLLECTION_SIZE_METHOD_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(Collection.class.getName())
            .named("size")
            .withNoParameters();

    private static final Matcher<ExpressionTree> INT_ZERO = Matchers.intLiteral(0);

    @Override
    public Description matchBinary(BinaryTree tree, VisitorState state) {
        Optional<EqualsZeroExpression> maybeEqualsZeroExpression = getEqualsZeroExpression(tree, state);
        if (maybeEqualsZeroExpression.isEmpty()) {
            return Description.NO_MATCH;
        }

        EqualsZeroExpression equalsZeroExpression = maybeEqualsZeroExpression.get();
        ExpressionTree operand = equalsZeroExpression.operand();
        ExpressionTree collectionInstance = ASTHelpers.getReceiver(operand);

        if (isInIsEmptyDefinition(state) && (collectionInstance == null || isExpressionThis(collectionInstance))) {
            return Description.NO_MATCH;
        }

        if (COLLECTION_SIZE_METHOD_MATCHER.matches(operand, state)) {
            return describeMatch(
                    tree,
                    SuggestedFix.replace(
                            tree,
                            (equalsZeroExpression.type() == ExpressionType.NEQ ? "!" : "")
                                    + (collectionInstance != null
                                            ? state.getSourceForNode(collectionInstance) + "."
                                            : "")
                                    + "isEmpty()"));
        }

        return Description.NO_MATCH;
    }

    private static boolean isInIsEmptyDefinition(VisitorState state) {
        MethodTree methodTree = ASTHelpers.findEnclosingNode(state.getPath(), MethodTree.class);
        return methodTree != null && methodTree.getName().contentEquals("isEmpty");
    }

    private static boolean isExpressionThis(ExpressionTree expressionTree) {
        return ((expressionTree instanceof IdentifierTree)
                && ((JCTree.JCIdent) expressionTree).getName().contentEquals("this"));
    }

    private static Optional<EqualsZeroExpression> getEqualsZeroExpression(BinaryTree tree, VisitorState state) {
        ExpressionType ret;
        switch (tree.getKind()) {
            case EQUAL_TO:
                ret = ExpressionType.EQ;
                break;
            case NOT_EQUAL_TO:
                ret = ExpressionType.NEQ;
                break;
            default:
                return Optional.empty();
        }
        ExpressionTree leftOperand = tree.getLeftOperand();
        ExpressionTree rightOperand = tree.getRightOperand();

        if (INT_ZERO.matches(leftOperand, state)) {
            return Optional.of(EqualsZeroExpression.builder()
                    .type(ret)
                    .operand(rightOperand)
                    .build());
        } else if (INT_ZERO.matches(rightOperand, state)) {
            return Optional.of(EqualsZeroExpression.builder()
                    .type(ret)
                    .operand(leftOperand)
                    .build());
        }

        return Optional.empty();
    }

    enum ExpressionType {
        EQ,
        NEQ
    }

    @Immutable
    interface EqualsZeroExpression {
        ExpressionType type();

        ExpressionTree operand();

        class Builder extends ImmutableEqualsZeroExpression.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }
}
