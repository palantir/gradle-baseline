/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferSafeLoggableExceptions",
        category = BugPattern.Category.ONE_OFF,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Throw SafeLoggable exceptions to ensure the message will not be redacted")
public class PreferSafeLoggableExceptions extends BugChecker implements BugChecker.NewClassTreeMatcher {

    private static final long serialVersionUID = 1L;
    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    // https://github.com/palantir/safe-logging/tree/develop/preconditions/src/main/java/com/palantir/logsafe/exceptions
    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        List<? extends ExpressionTree> args = tree.getArguments();
        Optional<? extends ExpressionTree> messageArg = args.stream()
                .filter(arg -> ASTHelpers.isSameType(ASTHelpers.getType(arg),
                        state.getTypeFromString("java.lang.String"), state))
                .reduce((one, two) -> one);
        // if (!messageArg.isPresent() || compileTimeConstExpressionMatcher.matches(messageArg.get(), state)) {
        if (!messageArg.isPresent()) {
            return Description.NO_MATCH;
        }

        if (Matchers.isSameType(IllegalArgumentException.class).matches(tree.getIdentifier(), state)) {
            return buildDescription(tree)
                    .setMessage("Prefer SafeIllegalArgumentException from com.palantir.safe-logging:preconditions")
                    .build();
        }

        if (Matchers.isSameType(IllegalStateException.class).matches(tree.getIdentifier(), state)) {
            return buildDescription(tree)
                    .setMessage("Prefer SafeIllegalStateException from com.palantir.safe-logging:preconditions")
                    .build();
        }

        if (Matchers.isSameType(IOException.class).matches(tree.getIdentifier(), state)) {
            return buildDescription(tree)
                    .setMessage("Prefer SafeIOException from com.palantir.safe-logging:preconditions")
                    .build();
        }

        if (Matchers.isSameType(NullPointerException.class).matches(tree.getIdentifier(), state)) {
            return buildDescription(tree)
                    .setMessage("Prefer SafeNullPointerException from com.palantir.safe-logging:preconditions")
                    .build();
        }

        if (Matchers.isSameType(RuntimeException.class).matches(tree.getIdentifier(), state)) {
            return buildDescription(tree)
                    .setMessage("Prefer SafeRuntimeException from com.palantir.safe-logging:preconditions")
                    .build();
        }

        return Description.NO_MATCH;
    }
}
