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

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ErrorProneToken;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import java.util.List;

/**
 * UnnecessaryLambdaArgumentParentheses provides similar functionality to the upstream UnnecessaryParentheses, but
 * specifically for single-parameter lambda arguments which are not covered by the existing check. Perhaps this can be
 * contributed upstream. There's an argument against combining the two because parentheses around lambda arguments
 * cannot be parsed directly from the AST where other parenthesis checked by UnnecessaryParentheses can.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "UnnecessaryLambdaArgumentParentheses",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Lambdas with a single parameter do not require argument parentheses.")
public final class UnnecessaryLambdaArgumentParentheses extends BugChecker
        implements BugChecker.LambdaExpressionTreeMatcher {

    @Override
    public Description matchLambdaExpression(LambdaExpressionTree tree, VisitorState state) {
        if (!quickCheck(tree, state)) {
            return Description.NO_MATCH;
        }
        // Avoid using getOffsetTokensForNode at this point because it's significantly more expensive
        List<ErrorProneToken> tokens = state.getTokensForNode(tree);
        int depth = 0;
        int identifiers = 0;
        for (int i = 0; i < tokens.size(); i++) {
            ErrorProneToken token = tokens.get(i);
            // parameters with types require parens
            if (token.kind() == Tokens.TokenKind.IDENTIFIER && ++identifiers > 1) {
                return Description.NO_MATCH;
            } else if (token.kind() == Tokens.TokenKind.ARROW) {
                return Description.NO_MATCH;
            } else if (token.kind() == Tokens.TokenKind.LPAREN) {
                depth++;
            } else if (token.kind() == Tokens.TokenKind.RPAREN && --depth == 0) {
                List<ErrorProneToken> offsetTokens = state.getOffsetTokensForNode(tree);
                ErrorProneToken firstToken = offsetTokens.get(0);
                ErrorProneToken offsetToken = offsetTokens.get(i);
                return buildDescription(tree.getParameters().get(0))
                        .addFix(SuggestedFix.builder()
                                .replace(firstToken.pos(), firstToken.endPos(), "")
                                .replace(offsetToken.pos(), offsetToken.endPos(), "")
                                .build())
                        .build();
            }
        }
        return Description.NO_MATCH;
    }

    // Fast check to rule out lambdas that don't violate this check without tokenizing the source.
    private static boolean quickCheck(LambdaExpressionTree tree, VisitorState state) {
        if (tree.getParameters().size() != 1) {
            return false;
        }
        int start = ((JCTree) tree).getStartPosition();
        if (start == -1) {
            return false;
        }
        CharSequence source = state.getSourceCode();
        if (source == null) {
            return false;
        }
        // Fast check to avoid tokenizing all lambdas unnecessarily.
        return source.charAt(start) == '(';
    }
}
