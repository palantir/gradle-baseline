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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreventUsingIncubatingMethods",
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "You should avoid using incubating methods where possible, since they have very weak stability"
                + " guarantees. You can explicitly disable this check on a case-by-case basis using"
                + " @SuppressWarnings(\"PreventUsingIncubatingMethods\"), or disable it globally for new projects.")
public final class PreventUsingIncubatingMethods extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher {

    /** The full path for the Incubating annotation. */
    private static final String INCUBATING = "com.palantir.conjure.java.lib.internal.Incubating";

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return checkTree(tree, ASTHelpers.getSymbol(tree), state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        return checkTree(tree, ASTHelpers.getSymbol(tree), state);
    }

    private Description checkTree(Tree tree, Symbol.MethodSymbol method, VisitorState state) {
        if (!ASTHelpers.hasAnnotation(method, INCUBATING, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("You should avoid calling incubating methods where possible in stable products, since"
                        + " they have weak stability guarantees. You can disable this check on a case-by-case basis"
                        + " using @SuppressWarnings(\"PreventUsingIncubatingMethods\"), or globally for new projects"
                        + " by altering your build configuration.")
                .build();
    }
}
