/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.bugpatterns.AbstractReturnValueIgnored;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.concurrent.ExecutorService;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ExecutorSubmitRunnableFutureIgnored",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Uncaught exceptions from ExecutorService.submit are not logged by the uncaught exception handler "
                + "because it is assumed that the returned future is used to watch for failures.\n"
                + "When the returned future is ignored, using ExecutorService.execute is preferred because "
                + "failures are recorded.")
public final class ExecutorSubmitRunnableFutureIgnored extends AbstractReturnValueIgnored {

    private static final Matcher<ExpressionTree> MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(ExecutorService.class.getName())
            .named("submit")
            .withParameters(Runnable.class.getName());

    @Override
    public Matcher<? super ExpressionTree> specializedMatcher() {
        return MATCHER;
    }

    // Override matchMethodInvocation from AbstractReturnValueIgnored to apply our suggested fix.
    @Override
    public Description matchMethodInvocation(MethodInvocationTree methodInvocationTree, VisitorState state) {
        Description description = super.matchMethodInvocation(methodInvocationTree, state);
        if (Description.NO_MATCH.equals(description)) {
            return description;
        }
        return buildDescription(methodInvocationTree)
                .addFix(MoreSuggestedFixes.renameMethodInvocation(methodInvocationTree, "execute", state))
                .build();
    }
}
