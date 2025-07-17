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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import java.io.IOException;
import java.io.UncheckedIOException;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Prefer UncheckedIOException or SafeUncheckedIoException when wrapping IOException so consumers can"
                + " more accurately categorize errors")
public final class PreferUncheckedIoException extends BugChecker implements NewClassTreeMatcher {

    private static final Matcher<ExpressionTree> STRING_MATCHER = Matchers.isSameType(String.class);
    private static final Matcher<ExpressionTree> IO_EXCEPTION_MATCHER = Matchers.isSubtypeOf(IOException.class);

    private static final Matcher<ExpressionTree> RUNTIME_EXCEPTION_MATCHER =
            MethodMatchers.constructor().forClass(RuntimeException.class.getName());
    private static final Matcher<ExpressionTree> SAFE_RUNTIME_EXCEPTION_MATCHER =
            MethodMatchers.constructor().forClass("com.palantir.logsafe.exceptions.SafeRuntimeException");

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (RUNTIME_EXCEPTION_MATCHER.matches(tree, state) && matchesArguments(tree, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFix.builder()
                            .addImport(UncheckedIOException.class.getName())
                            .replace(tree.getIdentifier(), UncheckedIOException.class.getSimpleName())
                            .build())
                    .build();
        }

        if (SAFE_RUNTIME_EXCEPTION_MATCHER.matches(tree, state) && matchesArguments(tree, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFix.builder()
                            .addImport("com.palantir.logsafe.exceptions.SafeUncheckedIoException")
                            .replace(tree.getIdentifier(), "SafeUncheckedIoException")
                            .build())
                    .build();
        }

        return Description.NO_MATCH;
    }

    private static boolean matchesArguments(NewClassTree tree, VisitorState state) {
        return switch (tree.getArguments().size()) {
            case 0 -> {
                yield false;
            }
            case 1 -> {
                yield IO_EXCEPTION_MATCHER.matches(tree.getArguments().get(0), state);
            }
            default -> {
                yield STRING_MATCHER.matches(tree.getArguments().get(0), state)
                        && IO_EXCEPTION_MATCHER.matches(tree.getArguments().get(1), state);
            }
        };
    }
}
