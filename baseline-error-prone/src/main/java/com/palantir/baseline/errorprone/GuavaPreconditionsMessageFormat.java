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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "GuavaPreconditionsMessageFormat",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Guava Preconditions.checkX() methods must use print-f style formatting.")
public final class GuavaPreconditionsMessageFormat extends PreconditionsMessageFormat {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> GUAVA_PRECONDITIONS_METHODS = MethodMatchers.staticMethod()
            .onClassAny("com.google.common.base.Preconditions")
            .withNameMatching(Pattern.compile("checkArgument|checkState|checkNotNull"));

    public GuavaPreconditionsMessageFormat() {
        super(GUAVA_PRECONDITIONS_METHODS);
    }

    @Override
    protected Description matchMessageFormat(MethodInvocationTree tree, String message, VisitorState state) {
        if (!message.contains("{}")) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("Use printf-style formatting in Guava Preconditions, not '{}' style formatting.")
                .build();
    }
}
