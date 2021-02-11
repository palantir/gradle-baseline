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
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.VariableTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ConsistentLoggerName",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Loggers created using getLogger(Class<?>) must be named 'log'.")
public final class ConsistentLoggerName extends BugChecker implements BugChecker.VariableTreeMatcher {

    private static final Matcher<VariableTree> matcher = Matchers.allOf(
            Matchers.isField(),
            Matchers.isStatic(),
            MoreMatchers.isFinal(),
            Matchers.isSubtypeOf("org.slf4j.Logger"),
            Matchers.variableInitializer(MethodMatchers.staticMethod()
                    .onClass("org.slf4j.LoggerFactory")
                    .named("getLogger")
                    // Only match the 'class' constructor
                    .withParameters(Class.class.getName())));

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        if (matcher.matches(tree, state) && !tree.getName().contentEquals("log")) {
            return buildDescription(tree)
                    .addFix(SuggestedFixes.renameVariable(tree, "log", state))
                    .build();
        }
        return Description.NO_MATCH;
    }
}
