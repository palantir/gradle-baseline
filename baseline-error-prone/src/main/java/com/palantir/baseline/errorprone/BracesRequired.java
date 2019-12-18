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
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import javax.annotation.Nullable;

@AutoService(BugChecker.class)
@BugPattern(
        name = "BracesRequired",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Braces are required for readability")
public final class BracesRequired extends BugChecker implements
        BugChecker.DoWhileLoopTreeMatcher,
        BugChecker.ForLoopTreeMatcher,
        BugChecker.EnhancedForLoopTreeMatcher,
        BugChecker.IfTreeMatcher,
        BugChecker.WhileLoopTreeMatcher {

    @Override
    public Description matchIf(IfTree tree, VisitorState state) {
        check(tree.getThenStatement(), state);
        check(tree.getElseStatement(), state);
        return Description.NO_MATCH;
    }

    @Override
    public Description matchWhileLoop(WhileLoopTree tree, VisitorState state) {
        check(tree.getStatement(), state);
        return Description.NO_MATCH;
    }

    @Override
    public Description matchDoWhileLoop(DoWhileLoopTree tree, VisitorState state) {
        check(tree.getStatement(), state);
        return Description.NO_MATCH;
    }

    @Override
    public Description matchEnhancedForLoop(EnhancedForLoopTree tree, VisitorState state) {
        check(tree.getStatement(), state);
        return Description.NO_MATCH;
    }

    @Override
    public Description matchForLoop(ForLoopTree tree, VisitorState state) {
        check(tree.getStatement(), state);
        return Description.NO_MATCH;
    }

    private void check(@Nullable StatementTree tree, VisitorState state) {
        if (tree != null && tree.getKind() == Tree.Kind.EXPRESSION_STATEMENT) {
            state.reportMatch(buildDescription(tree)
                    .addFix(SuggestedFix.replace(tree, "{" + state.getSourceForNode(tree) + "}"))
                    .build());
        }
    }
}
