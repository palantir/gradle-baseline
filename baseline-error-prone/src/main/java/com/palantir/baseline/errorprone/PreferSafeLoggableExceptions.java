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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferSafeLoggableExceptions",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        category = BugPattern.Category.ONE_OFF,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Throw SafeLoggable exceptions to ensure the exception message will not be redacted")
public final class PreferSafeLoggableExceptions extends BugChecker implements BugChecker.NewClassTreeMatcher {

    private static final long serialVersionUID = 1L;
    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    // https://github.com/palantir/safe-logging/tree/develop/preconditions/src/main/java/com/palantir/logsafe/exceptions
    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        List<? extends ExpressionTree> args = tree.getArguments();
        Optional<? extends ExpressionTree> messageArg = args.stream()
                .filter(arg -> ASTHelpers.isSameType(
                        ASTHelpers.getType(arg),
                        state.getTypeFromString("java.lang.String"),
                        state))
                .reduce((one, two) -> one);

        if (!messageArg.isPresent()) {
            return Description.NO_MATCH;
        }

        if (!compileTimeConstExpressionMatcher.matches(messageArg.get(), state)) {
            // ignore exceptions with non-constant messages to minimise the hits
            return Description.NO_MATCH;
        }

        Map<Class<?>, String> map = ImmutableMap.of(
                IllegalArgumentException.class, "SafeIllegalArgumentException",
                IllegalStateException.class, "SafeIllegalStateException",
                IOException.class, "SafeIoException",
                NullPointerException.class, "SafeNullPointerException",
                RuntimeException.class, "SafeRuntimeException");

        return map.entrySet().stream()
                .filter(entry -> Matchers.isSameType(entry.getKey()).matches(tree.getIdentifier(), state))
                .map(entry -> buildDescription(tree)
                        .setMessage("Prefer " + entry.getValue() + " from com.palantir.safe-logging:preconditions")
                        .addFix(SuggestedFix.builder()
                                .replace(tree.getIdentifier(), entry.getValue())
                                .addImport("com.palantir.logsafe.exceptions." + entry.getValue())
                                .build())
                        .build())
                .findAny()
                .orElse(Description.NO_MATCH);
    }
}
