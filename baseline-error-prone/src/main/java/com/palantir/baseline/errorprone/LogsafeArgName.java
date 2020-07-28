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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.tree.JCTree;
import java.util.List;
import java.util.Set;

@AutoService(BugChecker.class)
@BugPattern(
        name = "LogsafeArgName",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.ERROR,
        summary = "Prevent certain argument names from being logged as safe.")
public final class LogsafeArgName extends BugChecker implements MethodInvocationTreeMatcher {
    static final String UNSAFE_ARG_NAMES_FLAG = "LogsafeArgName:UnsafeArgNames";

    private static final Matcher<ExpressionTree> SAFE_ARG_OF =
            Matchers.staticMethod().onClass("com.palantir.logsafe.SafeArg").named("of");
    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    private final Set<String> unsafeParamNames;

    // Must have default constructor for service loading to work correctly
    public LogsafeArgName() {
        this.unsafeParamNames = ImmutableSet.of();
    }

    public LogsafeArgName(ErrorProneFlags flags) {
        this.unsafeParamNames =
                flags.getList(UNSAFE_ARG_NAMES_FLAG).map(ImmutableSet::copyOf).orElseGet(ImmutableSet::of);
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (unsafeParamNames.isEmpty() || !SAFE_ARG_OF.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        ExpressionTree argNameExpression = args.get(0);
        if (compileTimeConstExpressionMatcher.matches(argNameExpression, state)) {
            String argName = (String) ((JCTree.JCLiteral) argNameExpression).getValue();
            if (unsafeParamNames.stream().anyMatch(unsafeArgName -> unsafeArgName.equalsIgnoreCase(argName))) {
                SuggestedFix.Builder builder = SuggestedFix.builder();
                String unsafeArg = SuggestedFixes.qualifyType(state, builder, "com.palantir.logsafe.UnsafeArg");
                return buildDescription(tree)
                        .setMessage("Arguments with name '" + argName + "' must be marked as unsafe.")
                        .addFix(builder.replace(tree.getMethodSelect(), unsafeArg + ".of")
                                .build())
                        .build();
            }
        }

        return Description.NO_MATCH;
    }
}
