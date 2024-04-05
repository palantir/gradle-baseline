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
import com.google.errorprone.bugpatterns.BugChecker.LambdaExpressionTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Forbid gradle argument providers to be implemented by lambdas.")
public final class GradleCacheableArgumentProvider extends SuppressibleBugChecker implements LambdaExpressionTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<Tree> IS_COMMAND_LINE_ARGUMENT_PROVIDER =
            Matchers.isSubtypeOf("org.gradle.process.CommandLineArgumentProvider");

    @Override
    public Description matchLambdaExpression(LambdaExpressionTree tree, VisitorState state) {
        if (!IS_COMMAND_LINE_ARGUMENT_PROVIDER.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage("Gradle command line providers are not cacheable when implemented by lambdas")
                .build();
    }
}
