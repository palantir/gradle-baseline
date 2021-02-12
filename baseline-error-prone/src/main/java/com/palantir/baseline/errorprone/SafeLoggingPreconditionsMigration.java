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
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "SafeLoggingPreconditionsMigration",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "Prefer the non-deprecated safe-logging preconditions path. "
                + "See https://github.com/palantir/safe-logging/pull/515 for context. Using gradle baseline, "
                + "failures can be fixed automatically using "
                + "`./gradlew classes testClasses -PerrorProneApply=SafeLoggingPreconditionsMigration`")
public final class SafeLoggingPreconditionsMigration extends BugChecker implements BugChecker.MemberSelectTreeMatcher {

    private static final Matcher<Tree> LEGACY_PRECONDITIONS_MATCHER =
            Matchers.isSameType("com.palantir.logsafe.Preconditions");

    @Override
    public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        if (LEGACY_PRECONDITIONS_MATCHER.matches(tree, state)
                // Only attempt to warn or refactor when the preconditions dependency version is sufficiently new,
                // older versions which haven't deprecated "com.palantir.logsafe.Preconditions" won't have the
                // replacement.
                && ASTHelpers.hasAnnotation(ASTHelpers.getSymbol(tree), Deprecated.class, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFix.replace(tree, "com.palantir.logsafe.preconditions.Preconditions"))
                    .build();
        }
        return Description.NO_MATCH;
    }
}
