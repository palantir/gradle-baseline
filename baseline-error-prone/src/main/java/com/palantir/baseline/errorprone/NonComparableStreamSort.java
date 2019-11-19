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
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        name = "NonComparableStreamSort",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Stream.sorted() should only be called on streams of Comparable types.")
public final class NonComparableStreamSort extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> SORTED_CALL_ON_JAVA_STREAM_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(Stream.class.getName())
            .named("sorted")
            .withParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!SORTED_CALL_ON_JAVA_STREAM_MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        Type returnType = ASTHelpers.getReturnType(tree);
        if (returnType == null || returnType.getTypeArguments().size() != 1) {
            return Description.NO_MATCH;
        }
        Type streamParameterType = Iterables.getOnlyElement(returnType.getTypeArguments());
        if (ASTHelpers.isCastable(streamParameterType, state.getTypeFromString(Comparable.class.getName()), state)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage("Stream.sorted() should only be called on streams of Comparable types.")
                .build();
    }
}
