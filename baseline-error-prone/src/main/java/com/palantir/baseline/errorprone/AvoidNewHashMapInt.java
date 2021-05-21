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
import com.google.common.collect.Maps;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "AvoidNewHashMapInt",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "The HashMap(int) constructor is misleading, use Maps.newHashMapWithExpectedSize(int) instead.")
public final class AvoidNewHashMapInt extends BugChecker implements BugChecker.NewClassTreeMatcher {

    private static final long serialVersionUID = 1L;


    private static final Matcher<ExpressionTree> NEW_HASH_MAP = MethodMatchers.constructor()
            .forClass("java.util.HashMap")
            .withParameters("int");

    @Override
    public Description matchNewClass(
            NewClassTree tree, VisitorState state) {
        if (!NEW_HASH_MAP.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
        String newType = SuggestedFixes.qualifyType(state, fixBuilder, Maps.class.getName());
        String arg = tree.getArguments().isEmpty()
                ? ""
                : state.getSourceForNode(tree.getArguments().get(0));
        String replacement = newType + ".newHashMapWithExpectedSize(" + arg + ")";
        return buildDescription(tree)
                .setMessage("The HashMap(int) constructor is misleading: once the HashMap reaches 3/4 of the supplied"
                        + " size, it will double its internal storage array. Instead use Maps"
                        + ".newHashMapWithExpectedSize which behaves as expected."
                        + "See"
                                + " https://github.com/palantir/gradle-baseline/blob/develop/docs/best-practices/java-coding-guidelines/readme.md#avoid-new-HashMap(int)"
                                + " for more information.")
                .addFix(fixBuilder.replace(tree, replacement).build())
                .build();
    }
}
