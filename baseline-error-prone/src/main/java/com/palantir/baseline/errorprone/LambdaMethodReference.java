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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "LambdaMethodReference",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Lambda should be a method reference")
public final class LambdaMethodReference extends BugChecker implements BugChecker.LambdaExpressionTreeMatcher {

    private static final String MESSAGE = "Lambda should be a method reference";

    @Override
    public Description matchLambdaExpression(LambdaExpressionTree tree, VisitorState state) {
        // Only handle simple no-arg method references for the time being, don't worry about
        // simplifying map.forEach((k, v) -> func(k, v)) to map.forEach(this::func)
        if (!tree.getParameters().isEmpty()) {
            return Description.NO_MATCH;
        }
        LambdaExpressionTree.BodyKind bodyKind = tree.getBodyKind();
        Tree body = tree.getBody();
        // n.b. These checks are meant to avoid any and all cleverness. The goal is to be confident
        // that we can fix the most basic problems correctly, not to take risks and produce code
        // that may not compile.
        switch (bodyKind) {
            case EXPRESSION:
                if (!(body instanceof MethodInvocationTree)) {
                    return Description.NO_MATCH;
                }
                return checkMethodInvocation((MethodInvocationTree) body, tree, state);
            case STATEMENT:
                if (!(body instanceof BlockTree)) {
                    return Description.NO_MATCH;
                }
                BlockTree block = (BlockTree) body;
                if (block.getStatements().size() != 1) {
                    return Description.NO_MATCH;
                }
                StatementTree statement = block.getStatements().get(0);
                if (!(statement instanceof ReturnTree)) {
                    return Description.NO_MATCH;
                }
                ReturnTree returnStatement = (ReturnTree) statement;
                ExpressionTree returnExpression = returnStatement.getExpression();
                if (!(returnExpression instanceof MethodInvocationTree)) {
                    return Description.NO_MATCH;
                }
                return checkMethodInvocation((MethodInvocationTree) returnExpression, tree, state);
        }
        throw new IllegalStateException("Unexpected BodyKind: " + bodyKind);
    }

    private Description checkMethodInvocation(
            MethodInvocationTree methodInvocation, LambdaExpressionTree root, VisitorState state) {
        if (!methodInvocation.getArguments().isEmpty()
                || !methodInvocation.getTypeArguments().isEmpty()) {
            return Description.NO_MATCH;
        }
        Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodInvocation);
        if (methodSymbol == null) {
            // Should only ever occur if there are errors in the AST, allow the compiler to fail later
            return Description.NO_MATCH;
        }
        if (!methodSymbol.isStatic()) {
            // Only support static invocations for the time being to avoid erroneously
            // rewriting '() -> foo()' to 'ClassName::foo' instead of 'this::foo'
            // or suggesting '() -> foo.doWork().bar()' should be rewritten to 'foo.doWork()::bar',
            // which executes 'doWork' eagerly, even when the supplier is not used.
            return Description.NO_MATCH;
        }
        return buildDescription(root)
                .setMessage(MESSAGE)
                .addFix(buildFix(methodSymbol, root, state))
                .build();
    }

    private static Optional<SuggestedFix> buildFix(
            Symbol.MethodSymbol symbol, LambdaExpressionTree root, VisitorState state) {
        SuggestedFix.Builder builder = SuggestedFix.builder();
        return toMethodReference(SuggestedFixes.qualifyType(state, builder, symbol))
                .map(qualified -> builder.replace(root, qualified).build());
    }

    private static Optional<String> toMethodReference(String qualifiedMethodName) {
        int index = qualifiedMethodName.lastIndexOf('.');
        if (index > 0) {
            return Optional.of(
                    qualifiedMethodName.substring(0, index) + "::" + qualifiedMethodName.substring(index + 1));
        }
        return Optional.empty();
    }
}
