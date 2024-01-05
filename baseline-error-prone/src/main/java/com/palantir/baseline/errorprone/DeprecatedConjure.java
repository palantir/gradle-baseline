/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;

@AutoService(BugChecker.class)
@BugPattern(
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "You should not use Conjure endpoints that are marked for removal as that may block "
                + " upgrades in the near future. You can explicitly disable this check on a case-by-case basis using"
                + " @SuppressWarnings(\"DeprecatedConjure\").")
public final class DeprecatedConjure extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher {

    private static final String CONJURE_CLIENT_ENDPOINT = "com.palantir.conjure.java.lib.internal.ClientEndpoint";

    //TODO: do we want to do for @Generated("com.palantir.conjure.java.types.BeanGenerator") as well?  That would get
    // messy because the same objects are used on the server-side and we can't tell which is which.

    private static final Matcher<Tree> DEPRECATED_CONJURE_ENDPOINT_MATCHER = Matchers.allOf(
            Matchers.symbolHasAnnotation(Deprecated.class),
            Matchers.symbolHasAnnotation(CONJURE_CLIENT_ENDPOINT)
    );

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    private Description checkTree(Tree tree, VisitorState state) {
        if (!DEPRECATED_CONJURE_ENDPOINT_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // Further refine check to see if annotation has the forRemoval flag
        if (!ASTHelpers.getSymbol(tree).isDeprecatedForRemoval()) {
            return Description.NO_MATCH;
        }

        // Allow users to test deprecated endpoints in test code without complaining.
        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }

        return describeMatch(tree);
    }
}
