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
import com.google.errorprone.bugpatterns.StreamResourceLeak;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        name = "JooqResultStreamLeak",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Methods that return a stream or cursor on jOOQ's ResultQuery should be closed using "
                + "try-with-resources. Not doing so can result in leaked database resources (such as connections or "
                + "cursors) in code paths that throw an exception or fail to call #close().")
public final class JooqResultStreamLeak extends StreamResourceLeak {
    private static final Matcher<ExpressionTree> MATCHER =
            MethodMatchers.instanceMethod()
                .onDescendantOf("org.jooq.ResultQuery")
                .withAnyName();

    @Override
    public Description matchMethodInvocation(
            MethodInvocationTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        if (!isStreamOrCursor(tree, state)) {
            return Description.NO_MATCH;
        }

        return matchNewClassOrMethodInvocation(tree, state);
    }

    private static boolean isStreamOrCursor(MethodInvocationTree tree, VisitorState state) {
        boolean isStream = ASTHelpers.isSubtype(ASTHelpers.getReturnType(tree),
                state.getTypeFromString(Stream.class.getName()),
                state);

        boolean isCursor = ASTHelpers.isSubtype(ASTHelpers.getReturnType(tree),
                state.getTypeFromString("org.jooq.Cursor"),
                state);

        return isCursor || isStream;
    }
}
