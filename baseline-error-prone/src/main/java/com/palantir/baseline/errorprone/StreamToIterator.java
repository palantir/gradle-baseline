/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.stream.BaseStream;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary =
                "Converting from Stream to Iterator can result in collecting the entire stream in some cases. Prefer sticking with one paradigm or another unless absolutely necessary. (https://bugs.openjdk.org/browse/JDK-8267359)")
public final class StreamToIterator extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher {
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STREAM_TO_ITERATOR_INVOCATION = MethodMatchers.instanceMethod()
            .onDescendantOf(BaseStream.class.getName())
            .namedAnyOf("iterator", "spliterator")
            .withNoParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (STREAM_TO_ITERATOR_INVOCATION.matches(tree, state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        if (tree.getMode() != MemberReferenceTree.ReferenceMode.INVOKE) {
            return Description.NO_MATCH;
        }

        MethodSymbol method = ASTHelpers.getSymbol(tree);
        if (!(method.getSimpleName().contentEquals("iterator")
                || method.getSimpleName().contentEquals("spliterator"))) {
            return Description.NO_MATCH;
        }

        if (!method.getParameters().isEmpty()) {
            return Description.NO_MATCH;
        }

        Type baseStream = state.getTypeFromString(BaseStream.class.getName());
        if (!state.getTypes().isSubtype(method.owner.type, state.getTypes().erasure(baseStream))) {
            return Description.NO_MATCH;
        }

        return describeMatch(tree);
    }
}
