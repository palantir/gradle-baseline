/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.lang.reflect.InvocationTargetException;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.SUGGESTION,
        summary =
                "InvocationTargetException.getTargetException predates the general-purpose exception chaining "
                        + "facility. The Throwable.getCause() method is now the preferred means of obtaining this "
                        + "information. Source: "
                        + "https://docs.oracle.com/en/java/javase/17/docs/api//java.base/java/lang/reflect/InvocationTargetException.html#getTargetException()")
public final class InvocationTargetExceptionGetTargetException extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> ITE_GET_TARGET_EXCEPTION_MATCHER = Matchers.instanceMethod()
            .onDescendantOf(InvocationTargetException.class.getName())
            .named("getTargetException")
            .withNoParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return ITE_GET_TARGET_EXCEPTION_MATCHER.matches(tree, state)
                ? describeMatch(tree, SuggestedFixes.renameMethodInvocation(tree, "getCause", state))
                : Description.NO_MATCH;
    }
}
