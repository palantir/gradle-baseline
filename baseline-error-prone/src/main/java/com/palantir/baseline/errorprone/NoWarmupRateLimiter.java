/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        name = "NoWarmupRateLimiter",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Ensures Guava RateLimiter initializers specify a warm-up duration. The default Guava RateLimiter "
                + "without specifying a warm-up time creates a limiter with 0 permits, and starts acquiring them. "
                + "This causes an unexpected behavior, where at the beginning of the rate limiter's lifetime, the "
                + "rate is much lower than specified. Zero warm-up time should be preferred to no warm-up time to "
                + "ensure a max amount of permits are available at the start.")
public final class NoWarmupRateLimiter extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final String DURATION_NAME = "java.time.Duration";
    private static final String RATE_LIMITER_NAME = "com.google.common.util.concurrent.RateLimiter";
    private static final Matcher<ExpressionTree> RATELIMITER_CREATE_METHOD = MethodMatchers.staticMethod()
            .onClass(RATE_LIMITER_NAME)
            .named("create")
            .withParameters("double");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (RATELIMITER_CREATE_METHOD.matches(tree, state)) {
            List<? extends ExpressionTree> args = tree.getArguments();
            if (args.size() > 1) {
                return Description.NO_MATCH;
            }
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String durationType = SuggestedFixes.qualifyType(state, fix, DURATION_NAME);
            fix.replace(args.get(0), String.format("%s, %s.ZERO", state.getSourceForNode(args.get(0)), durationType));
            return buildDescription(tree)
                    .setMessage("RateLimiter.create() does not include a warmup time. "
                            + "Consider including Duration.ZERO as the default warmup.")
                    .addFix(fix.build())
                    .build();
        }
        return Description.NO_MATCH;
    }
}
