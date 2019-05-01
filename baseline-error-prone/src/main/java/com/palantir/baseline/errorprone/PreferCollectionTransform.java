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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferCollectionTransform",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Prefer Guava's Lists.transform or Collections2.transform instead of Iterables.transform when "
                + "first argument's declared type is a List or Collection type for performance reasons, "
                + "cf. https://google.github.io/guava/releases/23.0/api/docs/com/google/common/collect/Iterables.html#transform-java.lang.Iterable-com.google.common.base.Function-")
public final class PreferCollectionTransform extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> ITERABLES_TRANSFORM_MATCHER =
            MethodMatchers.staticMethod()
                    .onClass("com.google.common.collect.Iterables")
                    .named("transform");

    private static final Matcher<Tree> LIST_MATCHER = Matchers.isSubtypeOf("java.util.List");
    private static final Matcher<Tree> COLLECTION_MATCHER = Matchers.isSubtypeOf("java.util.Collection");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (ITERABLES_TRANSFORM_MATCHER.matches(tree, state)) {
            List<? extends ExpressionTree> args = tree.getArguments();
            if (args.size() > 1 && COLLECTION_MATCHER.matches(args.get(0), state)) {
                String qualifiedType;
                String errorMessage;
                SuggestedFix.Builder fix = SuggestedFix.builder();
                if (LIST_MATCHER.matches(args.get(0), state)) {
                    // Fail on any 'Iterables.transform(List, Function) invocation
                    qualifiedType = SuggestedFixes.qualifyType(state, fix, "com.google.common.collect.Lists");
                    errorMessage = "Prefer Lists.transform";
                } else {
                    // Fail on any 'Iterables.transform(Collection, Function) invocation
                    qualifiedType = SuggestedFixes.qualifyType(state, fix, "com.google.common.collect.Collections2");
                    errorMessage = "Prefer Collections2.transform";
                }
                String method = qualifiedType + ".transform";
                return buildDescription(tree)
                        .setMessage(errorMessage)
                        .addFix(fix.replace(tree.getMethodSelect(), method).build())
                        .build();
            }
        }

        return Description.NO_MATCH;
    }

}
