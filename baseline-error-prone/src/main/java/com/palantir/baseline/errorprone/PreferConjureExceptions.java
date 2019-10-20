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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.CompileTimeConstantExpressionMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferConjureExceptions",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Prefer throwing a ServiceException instead of jax-rs WebApplicationException subtypes.")
public final class PreferConjureExceptions extends BugChecker implements BugChecker.NewClassTreeMatcher {

    private static final String JAXRS_PACKAGE = "javax.ws.rs";
    private static final String WEB_APP_EXCEPTION_NAME = JAXRS_PACKAGE + ".WebApplicationException";
    private static final String CONJURE_ERROR_PACKAGE = "com.palantir.conjure.java.api.errors";
    private static final String CONJURE_ERROR_TYPE = CONJURE_ERROR_PACKAGE + ".ErrorType";
    private static final String CONJURE_SERVICE_EXCEPTION = CONJURE_ERROR_PACKAGE + ".ServiceException";

    private static final ImmutableMap<String, String> EXCEPTION_TO_ERROR_TYPE = ImmutableMap.<String, String>builder()
            // This should include the 401 UNAUTHENTICATED type once
            // https://github.com/palantir/conjure/pull/367 is released.
            .put("BadRequestException", "INVALID_ARGUMENT")
            .put("ForbiddenException", "PERMISSION_DENIED")
            .put("InternalServerErrorException", "INTERNAL")
            .put("NotFoundException", "NOT_FOUND")
            .build();

    private static final Matcher<ExpressionTree> compileTimeConstExpressionMatcher =
            new CompileTimeConstantExpressionMatcher();

    @Override
    @SuppressWarnings("CyclomaticComplexity")
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        Type type = ASTHelpers.getResultType(tree);
        if (type == null) {
            return Description.NO_MATCH;
        }
        if (!ASTHelpers.isCastable(type, state.getTypeFromString(WEB_APP_EXCEPTION_NAME), state)) {
            return Description.NO_MATCH;
        }
        // Conjure ServiceException cannot be subclassed.
        if (tree.getClassBody() != null) {
            return describeMatch(tree);
        }
        // Conjure ServiceException does not support type arguments.
        if (!tree.getTypeArguments().isEmpty()) {
            return describeMatch(tree);
        }
        Optional<String> maybeErrorType = replacementErrorType(type, state);
        if (!maybeErrorType.isPresent()) {
            return describeMatch(tree);
        }
        String errorType = maybeErrorType.get();
        List<? extends ExpressionTree> arguments = tree.getArguments();
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualifiedErrorType = SuggestedFixes.qualifyType(state, fix, CONJURE_ERROR_TYPE);
        String qualifiedServiceException = SuggestedFixes.qualifyType(state, fix, CONJURE_SERVICE_EXCEPTION);
        String errorArgument = qualifiedErrorType + '.' + errorType;
        // Supports no-arg and single-arg (cause) WebApplicationExceptions.
        if (arguments.isEmpty() || (arguments.size() == 1 && isCastable(arguments.get(0), Throwable.class, state))) {
            String replacementArguments = Streams.concat(
                    Stream.of(errorArgument),
                    tree.getArguments().stream().map(state::getSourceForNode))
                    .collect(Collectors.joining(", "));
            fix.replace(tree, "new " + qualifiedServiceException + '(' + replacementArguments + ')');
            return buildDescription(tree)
                    .addFix(fix.build())
                    .build();
        }
        // Exception includes a message string. In this case we provide an intermediate exception to escort the
        // message to the logger without serializing it to to clients, providing parity with the replaced code.
        if ((arguments.size() == 1 && isCastable(arguments.get(0), String.class, state))
                || (arguments.size() == 2 && isCastable(arguments.get(0), String.class, state)
                && isCastable(arguments.get(1), Throwable.class, state))) {
            ExpressionTree firstArgument = arguments.get(0);
            String qualifiedCauseName = SuggestedFixes.qualifyType(state, fix,
                    compileTimeConstExpressionMatcher.matches(firstArgument, state)
                            ? "com.palantir.logsafe.exceptions.SafeRuntimeException"
                            : RuntimeException.class.getName());
            fix
                    .replace(tree.getIdentifier(), qualifiedServiceException)
                    .replace(firstArgument, errorArgument + ", new " + qualifiedCauseName + '('
                            + state.getSourceForNode(firstArgument) + (arguments.size() == 1 ? ")" : ""));
            if (arguments.size() == 2) {
                fix.replace(arguments.get(1), state.getSourceForNode(arguments.get(1)) + ')');
            }
            return buildDescription(tree)
                    .addFix(fix.build())
                    .build();
        }
        return describeMatch(tree);
    }

    private static boolean isCastable(ExpressionTree argument, Class<?> expected, VisitorState state) {
        return ASTHelpers.isCastable(
                ASTHelpers.getResultType(argument),
                state.getTypeFromString(expected.getName()),
                state);
    }

    private static Optional<String> replacementErrorType(Type type, VisitorState state) {
        return EXCEPTION_TO_ERROR_TYPE.entrySet().stream()
                .filter(entry -> ASTHelpers.isSameType(
                        type, state.getTypeFromString(JAXRS_PACKAGE + '.' + entry.getKey()), state))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
