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
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(
        name = "UnsafeGaugeRegistration",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Using TaggedMetricRegistry.gauge is equivalent to map.putIfAbsent, and can result in subtle "
                + "resource leaks. Prefer replacing existing gauge values.\n"
                // This check may begin to fail after a version upgrade, where fixes aren't automatically
                // applied
                + "This can be fixed automatically using "
                + "./gradlew compileJava compileTestJava -PerrorProneApply=UnsafeGaugeRegistration")
public final class UnsafeGaugeRegistration extends AbstractReturnValueIgnored {

    private static final String TAGGED_REGISTRY = "com.palantir.tritium.metrics.registry.TaggedMetricRegistry";
    private static final String REGISTER_WITH_REPLACEMENT = "registerWithReplacement";
    private static final Matcher<ExpressionTree> MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(TAGGED_REGISTRY)
            .named("gauge")
            .withParameters("com.palantir.tritium.metrics.registry.MetricName", "com.codahale.metrics.Gauge");

    @Override
    public Matcher<? super ExpressionTree> specializedMatcher() {
        return MATCHER;
    }

    // Override matchMethodInvocation from AbstractReturnValueIgnored to apply our suggested fix.
    @Override
    public Description matchMethodInvocation(MethodInvocationTree methodInvocationTree, VisitorState state) {
        Description description = super.matchMethodInvocation(methodInvocationTree, state);
        if (Description.NO_MATCH.equals(description)
                || !hasRegisterWithReplacement(state)
                || TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }
        return buildDescription(methodInvocationTree)
                .addFix(MoreSuggestedFixes.renameMethodInvocation(
                        methodInvocationTree, REGISTER_WITH_REPLACEMENT, state))
                .build();
    }

    /** TaggedMetricRegistry.registerWithReplacement was added in Tritium 0.16.1, avoid flagging older versions. */
    private static boolean hasRegisterWithReplacement(VisitorState state) {
        Symbol symbol = state.getSymbolFromString(TAGGED_REGISTRY);
        if (!(symbol instanceof Symbol.ClassSymbol)) {
            return false;
        }
        Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) symbol;
        for (Symbol enclosed : classSymbol.getEnclosedElements()) {
            if (enclosed instanceof Symbol.MethodSymbol) {
                Symbol.MethodSymbol enclosedMethod = (Symbol.MethodSymbol) enclosed;
                if (enclosedMethod.name.contentEquals(REGISTER_WITH_REPLACEMENT)) {
                    return true;
                }
            }
        }
        return false;
    }
}
