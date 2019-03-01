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
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.SwitchTree;
import java.util.Objects;

@AutoService(BugChecker.class)
@BugPattern(
        name = "SwitchStatementDefaultCase",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary = "Avoid default cases in switch statements to correctly handle new enum values")
public final class SwitchStatementDefaultCase extends BugChecker implements BugChecker.SwitchTreeMatcher {

    @Override
    public Description matchSwitch(SwitchTree tree, VisitorState state) {
        if (hasDefaultCase(tree)) {
            return buildDescription(tree)
                    .setMessage("Avoid using default case in switch statement.")
                    .build();
        }

        return Description.NO_MATCH;
    }

    private boolean hasDefaultCase(SwitchTree tree) {
        return tree.getCases().stream()
                .map(CaseTree::getExpression)
                .anyMatch(Objects::isNull);
    }
}
