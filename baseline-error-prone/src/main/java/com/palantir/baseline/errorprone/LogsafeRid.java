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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "LogsafeRid",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "Prevent rids from being logged as safe.")
public final class LogsafeRid extends BugChecker implements MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> SAFE_ARG_OF =
            Matchers.staticMethod().onClass("com.palantir.logsafe.SafeArg").named("of");

    public LogsafeRid() {}

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!SAFE_ARG_OF.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        if (ASTHelpers.getReturnType(tree).toString().contains("com.palantir.ri.ResourceIdentifier")) {
            SuggestedFix.Builder builder = SuggestedFix.builder();
            String unsafeArg = SuggestedFixes.qualifyType(state, builder, "com.palantir.logsafe.UnsafeArg");
            return buildDescription(tree)
                    .setMessage("Arguments with with rid values are not guaranteed to be safe.")
                    .addFix(builder.replace(tree.getMethodSelect(), unsafeArg + ".of")
                            .build())
                    .build();
        }

        return Description.NO_MATCH;
    }
}
