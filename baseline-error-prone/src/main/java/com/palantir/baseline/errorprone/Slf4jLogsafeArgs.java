/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.ImmutableList.Builder;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "Slf4jLogsafeArgs",
        category = Category.ONE_OFF,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Allow only com.palantir.logsafe.Arg types as parameter inputs to slf4j log messages.")
public final class Slf4jLogsafeArgs extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> LOG_METHOD =
            Matchers.anyOf(
                    MethodMatchers.instanceMethod()
                            .onDescendantOf("org.slf4j.Logger")
                            .withNameMatching(Pattern.compile("trace|debug|info|warn|error")));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!LOG_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> allArgs = tree.getArguments();
        int lastIndex = allArgs.size() - 1;
        int startArg = ASTHelpers.isCastable(
                ASTHelpers.getType(allArgs.get(0)),
                state.getTypeFromString("org.slf4j.Marker"),
                state) ? 2 : 1;
        int endArg = ASTHelpers.isCastable(
                ASTHelpers.getType(allArgs.get(lastIndex)),
                state.getTypeFromString("java.lang.Exception"),
                state) ? lastIndex - 1 : lastIndex;

        Builder<Integer> badArgsBuilder = new Builder<>();
        for (int i = startArg; i <= endArg; i++) {
            if (!ASTHelpers.isCastable(ASTHelpers.getType(allArgs.get(i)),
                    state.getTypeFromString("com.palantir.logsafe.Arg"), state)) {
                badArgsBuilder.add(i);
            }
        }
        List<Integer> badArgs = badArgsBuilder.build();

        if (badArgs.isEmpty()) {
            return Description.NO_MATCH;
        } else {
            return buildDescription(tree)
                    .setMessage("slf4j log statement does not use logsafe parameters for arguments " + badArgs)
                    .build();
        }
    }
}
