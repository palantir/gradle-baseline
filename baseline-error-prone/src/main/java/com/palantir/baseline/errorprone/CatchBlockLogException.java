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
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "CatchBlockLogException",
        category = Category.ONE_OFF,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "log statement in catch block does not log the caught exception.")
public final class CatchBlockLogException extends BugChecker implements BugChecker.CatchTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> logMethod = MethodMatchers.instanceMethod()
            .onDescendantOf("org.slf4j.Logger")
            .withNameMatching(Pattern.compile("trace|debug|info|warn|error"));

    private static final Matcher<Tree> containslogMethod = Matchers.contains(
            Matchers.toType(ExpressionTree.class, logMethod));

    private static final Matcher<ExpressionTree> logException = Matchers.methodInvocation(
            logMethod, ChildMultiMatcher.MatchType.LAST, Matchers.isSubtypeOf(Throwable.class));

    private static final Matcher<Tree> containslogException = Matchers.contains(Matchers.toType(
            ExpressionTree.class, logException));

    @Override
    public Description matchCatch(CatchTree tree, VisitorState state) {
        if (containslogMethod.matches(tree, state) && !containslogException.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Catch block contains log statements but thrown exception is never logged.")
                    .build();
        }
        return Description.NO_MATCH;
    }

}
