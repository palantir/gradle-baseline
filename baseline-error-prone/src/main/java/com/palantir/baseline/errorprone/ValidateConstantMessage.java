/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ValidateConstantMessage",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Allow only constant messages to Validate.X() methods")
public final class ValidateConstantMessage extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> VALIDATE_METHODS = MethodMatchers.staticMethod()
            .onClassAny("org.apache.commons.lang3.Validate", "org.apache.commons.lang.Validate");

    private final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    private static final ImmutableMap<String, Integer> VALIDATE_METHODS_MESSAGE_ARGS =
            ImmutableMap.<String, Integer>builder()
                    .put("exclusiveBetween", 4)
                    .put("finite", 2)
                    .put("inclusiveBetween", 4)
                    .put("isAssignableFrom", 3)
                    .put("isInstanceOf", 3)
                    .put("isTrue", 2)
                    .put("matchesPattern", 3)
                    .put("noNullElements", 2)
                    .put("notBlank", 2)
                    .put("notEmpty", 2)
                    .put("notNaN", 2)
                    .put("notNull", 2)
                    .put("validIndex", 3)
                    .put("validState", 2)
                    .put("allElementsOfType", 3) // commons-lang 2.x only
                    .build();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!VALIDATE_METHODS.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        String methodName = ASTHelpers.getSymbol(tree).name.toString();
        if (!VALIDATE_METHODS_MESSAGE_ARGS.containsKey(methodName)) {
            return Description.NO_MATCH;
        }

        int messageArgNumber = VALIDATE_METHODS_MESSAGE_ARGS.get(methodName);
        List<? extends ExpressionTree> args = tree.getArguments();

        if (args.size() < messageArgNumber) {
            return Description.NO_MATCH;
        }

        ExpressionTree messageArg = args.get(messageArgNumber - 1);
        boolean isStringType = ASTHelpers.isSameType(
                ASTHelpers.getType(messageArg), state.getTypeFromString("java.lang.String"), state);
        boolean isConstantString = compileTimeConstExpressionMatcher.matches(messageArg, state);
        if (!isStringType || isConstantString) {
            return Description.NO_MATCH;
        }

        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("Validate.X() statement uses a non-constant message")
                .build();
    }
}
