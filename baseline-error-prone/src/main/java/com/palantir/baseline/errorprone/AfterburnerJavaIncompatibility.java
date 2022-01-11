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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "AfterburnerJavaIncompatibility",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "The AfterburnerModule is not compatible with jdk16+ and will cause failures at runtime. "
                + "Either remove AfterburnerModule, or replace it with 'conjure-java-jackson-optimizations' "
                + "ObjectMapperOptimizations.createModules() to take the runtime into account.")
public final class AfterburnerJavaIncompatibility extends BugChecker implements BugChecker.NewClassTreeMatcher {

    private static final Matcher<ExpressionTree> MATCHER =
            MethodMatchers.constructor().forClass("com.fasterxml.jackson.module.afterburner.AfterburnerModule");

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        return MATCHER.matches(tree, state) ? describeMatch(tree) : Description.NO_MATCH;
    }
}
