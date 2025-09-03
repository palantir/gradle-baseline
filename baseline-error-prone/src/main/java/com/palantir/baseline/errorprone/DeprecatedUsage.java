/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;

/**
 * This check is meant to replace usage of the `-Werror` and `-Xlint:deprecation` compiler flags, which cannot be
 *   automatically suppressed by suppressible-error-prone and therefore block library upgrades as soon as a deprecation
 *   is introduced that is being relied upon.
 * Instead, this check is meant to be auto-suppressed upon upgrades and prevent backsliding by unintentionally relying
 *   upon deprecated APIs.
 */
@AutoService(BugChecker.class)
@BugPattern(
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Deprecated APIs should not be relied upon as they may be removed in a future release.",
        altNames = {"deprecation", "removal"})
public final class DeprecatedUsage extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher,
                BugChecker.MemberReferenceTreeMatcher,
                BugChecker.MemberSelectTreeMatcher {

    private static final Matcher<Tree> DEPRECATED_SYMBOL =
            Matchers.symbolMatcher((symbol, state) -> symbol.isDeprecated() || symbol.isDeprecatedForRemoval());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    private Description checkTree(Tree tree, VisitorState state) {
        if (!DEPRECATED_SYMBOL.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Symbol symbol = ASTHelpers.getSymbol(tree);
        String qualifiedName = symbol == null
                ? null
                : symbol.owner.getQualifiedName() + "#"
                        + symbol.getQualifiedName().toString();
        String description = qualifiedName == null
                ? "Deprecated API usage - this is dangerous as it may be removed in a future release."
                : String.format(
                        "%s is deprecated - this is dangerous as it may be removed in a future release.",
                        qualifiedName);
        return buildDescription(tree).setMessage(description).build();
    }
}
