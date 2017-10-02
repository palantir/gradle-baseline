/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ValidateConstantMessage",
        category = Category.ONE_OFF, // or APACHE
        severity = SeverityLevel.ERROR,
        summary = "Allow only constant messages to Validate.X() methods")
public final class ValidateConstantMessage extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> VALIDATE_METHODS =
            MethodMatchers.staticMethod()
                    .onClassAny("org.apache.commons.lang3.Validate", "org.apache.commons.lang.Validate");
    private static final Matcher<ExpressionTree> VALIDATE_METHODS_MESSAGE_THIRD_PARAM =
            Matchers.anyOf(
                    MethodMatchers.staticMethod()
                            .onClassAny("org.apache.commons.lang.Validate")
                            .named("allElementsOfType"),
                    MethodMatchers.staticMethod()
                            .onClassAny("org.apache.commons.lang3.Validate")
                            .withNameMatching(
                                    Pattern.compile("validIndex|matchesPattern|isInstanceOf|isAssignableFrom")));

    private static final Matcher<ExpressionTree> VALIDATE_METHODS_MESSAGE_FOURTH_PARAM =
            MethodMatchers.staticMethod()
                    // no org.apache.commons.lang.Validate methods have the message as the fourth arg
                    .onClass("org.apache.commons.lang3.Validate")
                    .withNameMatching(Pattern.compile("inclusiveBetween|exclusiveBetween"));

    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!VALIDATE_METHODS.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        int messageArgIndex;
        if (VALIDATE_METHODS_MESSAGE_FOURTH_PARAM.matches(tree, state)) {
            messageArgIndex = 3;
        } else if (VALIDATE_METHODS_MESSAGE_THIRD_PARAM.matches(tree, state)) {
            messageArgIndex = 2;
        } else {
            messageArgIndex = 1;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        Optional<? extends ExpressionTree> messageArg = messageArgIndex < args.size()
                ? Optional.of(args.get(messageArgIndex))
                : Optional.empty();

        if (!messageArg.isPresent() || compileTimeConstExpressionMatcher.matches(messageArg.get(), state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).setMessage(
                "Validate.X() statement uses a non-constant message").build();
    }
}
