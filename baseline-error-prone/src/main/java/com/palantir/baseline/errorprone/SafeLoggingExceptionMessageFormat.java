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
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        name = "SafeLoggingExceptionMessageFormat",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "SafeLoggable exceptions do not interpolate parameters")
public final class SafeLoggingExceptionMessageFormat extends BugChecker implements BugChecker.NewClassTreeMatcher {

    private static final long serialVersionUID = 1L;

    // github.com/palantir/safe-logging/tree/develop/preconditions/src/main/java/com/palantir/logsafe/exceptions
    private static final Matcher<ExpressionTree> STANDARD_SAFE_LOGGABLE_EXCEPTIONS = Matchers.anyOf(
            Matchers.isSameType("com.palantir.logsafe.exceptions.SafeIllegalArgumentException"),
            Matchers.isSameType("com.palantir.logsafe.exceptions.SafeIllegalStateException"),
            Matchers.isSameType("com.palantir.logsafe.exceptions.SafeIoException"),
            Matchers.isSameType("com.palantir.logsafe.exceptions.SafeNullPointerException"),
            Matchers.isSameType("com.palantir.logsafe.exceptions.SafeRuntimeException"));

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (!STANDARD_SAFE_LOGGABLE_EXCEPTIONS.matches(tree.getIdentifier(), state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();

        if (args.isEmpty()) {
            return Description.NO_MATCH;
        }

        ExpressionTree messageArg = args.get(0);
        if (!messageArg.getKind().equals(Tree.Kind.STRING_LITERAL)) {
            return Description.NO_MATCH;
        }

        if (!(messageArg instanceof LiteralTree)) {
            return Description.NO_MATCH;
        }
        LiteralTree literalTreeMessageArg = (LiteralTree) messageArg;

        Object value = literalTreeMessageArg.getValue();

        if (!(value instanceof String)) {
            return Description.NO_MATCH;
        }
        String message = (String) value;

        if (message.contains("{}")) {
            return buildDescription(tree)
                    .setMessage("Do not use slf4j-style formatting in logsafe Exceptions. "
                            + "Logsafe exceptions provide a simple message and key-value pairs of arguments, "
                            + "no interpolation is performed.")
                    .build();
        }
        return Description.NO_MATCH;
    }
}
