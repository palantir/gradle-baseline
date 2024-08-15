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
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.ErrorProneToken;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.parser.Tokens.TokenKind;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "The `var` keyword results in illegible code in most cases and should not be used.")
public final class VarUsage extends BugChecker implements BugChecker.VariableTreeMatcher {

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        Tree typeTree = tree.getType();
        // The AST doesn't differentiate between 'var' and a concrete type, so we check the source
        // prior to tokenizing the variable tree.
        String sourceType = state.getSourceForNode(typeTree);
        if (sourceType != null) {
            return Description.NO_MATCH;
        }
        TreePath parentPath = state.getPath().getParentPath();
        if (parentPath == null) {
            return Description.NO_MATCH;
        }
        Tree parentTree = parentPath.getLeaf();
        if (parentTree instanceof LambdaExpressionTree) {
            // Lambdas may take the form: var -> var.foo()
            return Description.NO_MATCH;
        }
        for (ErrorProneToken token : state.getOffsetTokensForNode(tree)) {
            if (token.kind() == TokenKind.IDENTIFIER
                    && token.hasName()
                    && token.name().contentEquals("var")) {
                SuggestedFix.Builder fix = SuggestedFix.builder();
                return buildDescription(tree)
                        .addFix(fix.replace(
                                        token.pos(),
                                        token.endPos(),
                                        SuggestedFixes.prettyType(state, fix, ASTHelpers.getType(typeTree)))
                                .build())
                        .build();
            }
        }
        return Description.NO_MATCH;
    }
}
