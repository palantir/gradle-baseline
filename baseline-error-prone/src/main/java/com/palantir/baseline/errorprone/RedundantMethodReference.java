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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.Set;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        name = "RedundantMethodReference",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Redundant method reference to the same type")
public final class RedundantMethodReference extends BugChecker implements BugChecker.MemberReferenceTreeMatcher {

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        if (tree.getMode() != MemberReferenceTree.ReferenceMode.INVOKE) {
            return Description.NO_MATCH;
        }
        if (!(tree instanceof JCTree.JCMemberReference)) {
            return Description.NO_MATCH;
        }
        JCTree.JCMemberReference jcMemberReference = (JCTree.JCMemberReference) tree;
        // Only support expression::method, not static method references or unbound method references (e.g. List::add)
        if (jcMemberReference.kind != JCTree.JCMemberReference.ReferenceKind.BOUND) {
            return Description.NO_MATCH;
        }
        Type rawResultType = ASTHelpers.getResultType(tree.getQualifierExpression());
        if (rawResultType == null) {
            return Description.NO_MATCH;
        }
        Type treeType = ASTHelpers.getType(tree);
        if (treeType == null) {
            return Description.NO_MATCH;
        }
        if (!state.getTypes().isAssignable(rawResultType, treeType)) {
            return Description.NO_MATCH;
        }
        Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        if (methodSymbol == null) {
            return Description.NO_MATCH;
        }
        // Make sure the same method is being overridden, it's important not to change method invocations on types
        // that also happen to implement the resulting functional interface.
        Set<Symbol.MethodSymbol> matching = ASTHelpers.findMatchingMethods(
                methodSymbol.name,
                symbol -> symbol.getModifiers().contains(Modifier.ABSTRACT)
                        && methodSymbol.overrides(symbol, symbol.enclClass(), state.getTypes(), true),
                treeType,
                state.getTypes());
        // Do not allow any ambiguity, size must be exactly one.
        if (matching.size() != 1) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .addFix(SuggestedFix.builder()
                        .replace(tree, state.getSourceForNode(tree.getQualifierExpression()))
                        .build())
                .build();
    }
}
