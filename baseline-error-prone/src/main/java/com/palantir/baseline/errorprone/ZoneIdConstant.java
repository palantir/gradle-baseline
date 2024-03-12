/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.time.ZoneId;
import java.time.ZoneOffset;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Prefer ZoneId constants.")
public final class ZoneIdConstant extends BaselineBugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> ZONE_ID_OF =
            Matchers.staticMethod().onClass(ZoneId.class.getName()).named("of").withParameters(String.class.getName());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (ZONE_ID_OF.matches(tree, state)) {
            String zone = ASTHelpers.constValue(tree.getArguments().get(0), String.class);
            if (zone != null && (zone.equals("Z") || zone.equals("UTC"))) {
                SuggestedFix.Builder fix = SuggestedFix.builder();
                return buildDescription(tree)
                        .addFix(fix.replace(
                                        tree,
                                        SuggestedFixes.qualifyType(state, fix, ZoneOffset.class.getName()) + ".UTC")
                                .build())
                        .build();
            }
        }

        return Description.NO_MATCH;
    }
}
