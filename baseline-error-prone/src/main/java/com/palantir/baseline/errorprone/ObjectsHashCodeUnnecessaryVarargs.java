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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ObjectsHashCodeUnnecessaryVarargs",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "java.util.Objects.hash(non-varargs) should be replaced with java.util.Objects.hashCode(value) "
                + "to avoid unnecessary varargs array allocations.")
public final class ObjectsHashCodeUnnecessaryVarargs extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> HASH_MATCHER =
            MethodMatchers.staticMethod().onClass("java.util.Objects").named("hash");

    private static final Matcher<ExpressionTree> OBJECT_ARRAY_MATCHER =
            Matchers.typePredicateMatcher((type, state) -> state.getTypes().isArray(type)
                    && state.getTypes()
                            .isSameType(
                                    state.getTypes().elemtype(type), state.getTypeFromString(Object.class.getName())));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (HASH_MATCHER.matches(tree, state) && tree.getArguments().size() == 1) {
            ExpressionTree argument = tree.getArguments().get(0);
            if (!OBJECT_ARRAY_MATCHER.matches(argument, state)) {
                return buildDescription(tree)
                        .addFix(SuggestedFixes.renameMethodInvocation(tree, "hashCode", state))
                        .build();
            }
        }
        return Description.NO_MATCH;
    }
}
