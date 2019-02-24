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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.predicates.TypePredicates;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "MultimapKeys",
        category = BugPattern.Category.GUAVA,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Multimap.keys() is usually an error. Did you mean to use Multimap.keySet()?")
public final class MultimapKeys extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;
    private static final String errorMsg =
            "Multimap.keys() is usually an error. Did you mean to use Multimap.keySet()?";

    private static final Matcher<ExpressionTree> multimapKeysMethod = MethodMatchers.instanceMethod()
            .onClass(TypePredicates.isDescendantOf("com.google.common.collect.Multimap"))
            .withNameMatching(Pattern.compile("keys"));

    @Override
    public Description matchMethodInvocation(
            MethodInvocationTree tree, VisitorState state) {
        if (!multimapKeysMethod.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage(errorMsg)
                .build();
    }
}
