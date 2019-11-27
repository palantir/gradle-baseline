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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.palantir.ri.ResourceIdentifier;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.SimpleTreeVisitor;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "Slf4jLogsafeArgs",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "RIDs should only be logged using the RidArg.of static helper.")
public final class RidSafeLogging extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> LOG_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .withNameMatching(Pattern.compile("trace|debug|info|warn|error"));
    private static final Matcher<ExpressionTree> RID = MoreMatchers.isSubtypeOf(ResourceIdentifier.class);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!LOG_METHOD.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        List<? extends ExpressionTree> allArgs = tree.getArguments();
        ImmutableList.Builder<Integer> ridArgsWithoutHelperBuilder = ImmutableList.builder();
        for (int i = 0; i < allArgs.size(); ++i) {
            Optional<ExpressionTree> result = allArgs.get(i).accept(RidArgVisitor.INSTANCE, state);
            if (result.isPresent()) {
                ridArgsWithoutHelperBuilder.add(i);
            }
        }
        List<Integer> ridArgsWithoutHelper = ridArgsWithoutHelperBuilder.build();
        if (ridArgsWithoutHelper.isEmpty() || TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        } else {
            return buildDescription(tree)
                    .setMessage("Log statments do not use the RidArg.of() helper for arguments "
                            + ridArgsWithoutHelper)
                    .build();
        }
    }

    /** Returns the throwable argument from SafeArg.of(name, throwable) or UnsafeArg.of(name, throwable). */
    private static final class RidArgVisitor extends SimpleTreeVisitor<Optional<ExpressionTree>, VisitorState> {
        private static final RidSafeLogging.RidArgVisitor INSTANCE = new RidSafeLogging.RidArgVisitor();

        private static final Matcher<ExpressionTree> RID_ARG_FACTORY = Matchers.staticMethod()
                .onClassAny("com.palantir.logsafe.SafeArg", "com.palantir.logsafe.UnsafeArg")
                .named("of")
                .withParameters(String.class.getName(), Object.class.getName());

        private static final Matcher<ExpressionTree> RID_ARG = Matchers.methodInvocation(
                RID_ARG_FACTORY, ChildMultiMatcher.MatchType.AT_LEAST_ONE, RID);

        private RidArgVisitor() {
            super(Optional.empty());
        }

        @Override
        public Optional<ExpressionTree> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            if (RID_ARG.matches(node, state)) {
                return Optional.of(node.getArguments().get(1));
            }
            return Optional.empty();
        }
    }

}
