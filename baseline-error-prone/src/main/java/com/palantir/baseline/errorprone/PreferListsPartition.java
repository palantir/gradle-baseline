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
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferListsPartition",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.WARNING,
        summary = "Prefer Guava's Lists.partition(List, int) instead of Iterables.partition(Iterable, int) when "
                + "first argument's declared type is a list for performance reasons, "
                + "cf. https://github.com/palantir/gradle-baseline/issues/621")
public final class PreferListsPartition extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final String ERROR_MESSAGE = "Prefer Lists.partition";

    private static final Matcher<ExpressionTree> ITERABLES_PARTITION_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.google.common.collect.Iterables")
            .named("partition")
            .withParameters("java.lang.Iterable", "int");

    private static final Matcher<Tree> LIST_MATCHER = MoreMatchers.isSubtypeOf("java.util.List");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (ITERABLES_PARTITION_MATCHER.matches(tree, state)) {
            List<? extends ExpressionTree> args = tree.getArguments();
            if (args.size() <= 1) {
                return Description.NO_MATCH;
            }

            if (LIST_MATCHER.matches(args.get(0), state)) {
                // Fail on any 'Iterables.partition(List, int) invocation
                SuggestedFix.Builder fix = SuggestedFix.builder();
                String qualifiedType = MoreSuggestedFixes.qualifyType(state, fix, "com.google.common.collect.Lists");
                String method = qualifiedType + ".partition";
                return buildDescription(tree)
                        .setMessage(ERROR_MESSAGE)
                        .addFix(fix.replace(tree.getMethodSelect(), method).build())
                        .build();
            }
        }

        return Description.NO_MATCH;
    }
}
