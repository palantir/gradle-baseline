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
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree.Kind;

@AutoService(BugChecker.class)
@BugPattern(
        name = "OptionalOrElseMethodInvocation",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.ERROR,
        summary = "Expression passed to Optional#orElse invokes a method, use Optional#orElseGet instead")
public final class OptionalOrElseMethodInvocation extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> OR_ELSE_METHOD =
            MethodMatchers.instanceMethod().onExactClass("java.util.Optional").named("orElse");

    private static final Matcher<ExpressionTree> METHOD_INVOCATIONS = Matchers.anyOf(
            MethodInvocationMatcher.INSTANCE,
            Matchers.contains(ExpressionTree.class, MethodInvocationMatcher.INSTANCE));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!OR_ELSE_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        ExpressionTree orElseArg = tree.getArguments().get(0);

        if (!METHOD_INVOCATIONS.matches(orElseArg, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("Expression passed to Optional#orElse invokes a method")
                .addFix(SuggestedFix.builder()
                        .postfixWith(tree.getMethodSelect(), "Get")
                        .prefixWith(orElseArg, "() -> ")
                        .build())
                .build();
    }

    private enum MethodInvocationMatcher implements Matcher<ExpressionTree> {
        INSTANCE;

        @Override
        public boolean matches(ExpressionTree tree, VisitorState state) {
            Kind kind = tree.getKind();
            return kind == Kind.NEW_CLASS || kind == Kind.METHOD_INVOCATION;
        }
    }
}
