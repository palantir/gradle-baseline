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
import com.google.common.util.concurrent.RateLimiter;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.ChildMultiMatcher.MatchType;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.time.Duration;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ZeroWarmupRateLimiter",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "RateLimiters with zero warmup duration do not rate limit. "
                + "Tracked at https://github.com/google/guava/issues/2730")
public final class ZeroWarmupRateLimiter extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> MATCHER = Matchers.methodInvocation(
            MethodMatchers.staticMethod()
                    .onClass(RateLimiter.class.getName())
                    .named("create")
                    .withParameters("double", Duration.class.getName()),
            MatchType.LAST,
            ZeroWarmupRateLimiter::isDurationZero);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (MATCHER.matches(tree, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFix.replace(
                            state.getEndPosition(tree.getArguments().get(0)),
                            state.getEndPosition(tree.getArguments().get(1)),
                            ""))
                    .build();
        }
        return Description.NO_MATCH;
    }

    /**
     * Returns true if the input tree is {@link Duration#ZERO}.
     * Note this only supports the constant reference for now, not the value+unit overload or {@code Duration.ofX(0)}.
     */
    private static boolean isDurationZero(ExpressionTree expressionTree, VisitorState state) {
        Symbol symbol = ASTHelpers.getSymbol(expressionTree);
        if (symbol != null && symbol.isStatic() && symbol instanceof VarSymbol) {
            VarSymbol varSymbol = (VarSymbol) symbol;
            return varSymbol.name.contentEquals("ZERO")
                    && state.getTypes()
                            .isSameType(varSymbol.owner.type, state.getTypeFromString(Duration.class.getName()));
        }
        return false;
    }
}
