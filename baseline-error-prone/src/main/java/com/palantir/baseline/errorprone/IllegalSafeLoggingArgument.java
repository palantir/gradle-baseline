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
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;

/**
 * Ensures that safe-logging annotated elements are handled correctly by annotated method parameters.
 * Potential future work:
 * <ul>
 *     <li>Suggest replacing {@code UnsafeArg.of(name, verifiableSafeValue)} with
 *     {@code SafeArg.of(name, verifiableSafeValue)}</li>
 *     <li>We could check return statements in methods annotated for
 *     safety to require consistency</li>
 *     <li>Enforce propagation of safety annotations from fields and types to types which encapsulate them.</li>
 *     <li>More complex flow analysis to ensure safety information is respected.</li>
 * </ul>
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "IllegalSafeLoggingArgument",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "safe-logging annotations must agree between args and method parameters")
public final class IllegalSafeLoggingArgument extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final String SAFE = "com.palantir.logsafe.Safe";
    private static final String UNSAFE = "com.palantir.logsafe.Unsafe";
    private static final String DO_NOT_LOG = "com.palantir.logsafe.DoNotLog";

    private static final Matcher<ExpressionTree> TO_STRING =
            MethodMatchers.instanceMethod().anyClass().named("toString").withNoParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        List<? extends ExpressionTree> arguments = tree.getArguments();
        if (arguments.isEmpty()) {
            return Description.NO_MATCH;
        }
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        if (methodSymbol == null) {
            return Description.NO_MATCH;
        }
        List<VarSymbol> parameters = methodSymbol.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VarSymbol parameter = parameters.get(i);
            Safety parameterSafety = getSafety(parameter, state);
            if (parameterSafety == Safety.UNKNOWN) {
                // Fast path, avoid analysis when the value isn't provided to a safety-aware consumer
                continue;
            }

            int limit = methodSymbol.isVarArgs() && i == parameters.size() - 1 ? arguments.size() : i + 1;
            for (int j = i; j < limit; j++) {
                ExpressionTree argument = arguments.get(j);
                Safety argumentSafety = getSafety(argument, state);
                if (!parameterSafety.allowsValueWith(argumentSafety)) {
                    // use state.reportMatch to report all failing arguments if multiple are invalid
                    state.reportMatch(buildDescription(argument)
                            .setMessage(String.format(
                                    "Dangerous argument value: arg is '%s' but the parameter requires '%s'.",
                                    argumentSafety, parameterSafety))
                            .build());
                }
            }
        }
        return Description.NO_MATCH;
    }

    private static Safety getSafety(ExpressionTree tree, VisitorState state) {
        Tree argument = ASTHelpers.stripParentheses(tree);
        // Check annotations on the result type
        Type resultType = ASTHelpers.getResultType(tree);
        if (resultType != null) {
            Safety resultTypeSafety = getSafety(resultType.tsym, state);
            if (resultTypeSafety != Safety.UNKNOWN) {
                return resultTypeSafety;
            }
        }
        // Unwrap type-casts: 'Object value = (Object) unsafeType;' is still unsafe.
        if (argument instanceof TypeCastTree) {
            TypeCastTree typeCastTree = (TypeCastTree) argument;
            return getSafety(typeCastTree.getExpression(), state);
        }
        // If the argument is a method invocation, check the method for safety annotations
        if (argument instanceof MethodInvocationTree) {
            MethodInvocationTree argumentInvocation = (MethodInvocationTree) argument;
            MethodSymbol methodSymbol = ASTHelpers.getSymbol(argumentInvocation);
            if (methodSymbol != null) {
                Safety methodSafety = getSafety(methodSymbol, state);
                // non-annotated toString inherits type-level safety.
                if (methodSafety == Safety.UNKNOWN && TO_STRING.matches(argumentInvocation, state)) {
                    return getSafety(ASTHelpers.getReceiver(argumentInvocation), state);
                }
                return methodSafety;
            }
        }
        // Check the argument symbol itself:
        Symbol argumentSymbol = ASTHelpers.getSymbol(argument);
        return argumentSymbol != null ? getSafety(argumentSymbol, state) : Safety.UNKNOWN;
    }

    private static Safety getSafety(Symbol symbol, VisitorState state) {
        if (ASTHelpers.hasAnnotation(symbol, DO_NOT_LOG, state)) {
            return Safety.DO_NOT_LOG;
        }
        if (ASTHelpers.hasAnnotation(symbol, UNSAFE, state)) {
            return Safety.UNSAFE;
        }
        if (ASTHelpers.hasAnnotation(symbol, SAFE, state)) {
            return Safety.SAFE;
        }
        return Safety.UNKNOWN;
    }

    private enum Safety {
        UNKNOWN() {
            @Override
            boolean allowsValueWith(Safety _valueSafety) {
                // No constraints when safety isn't specified
                return true;
            }
        },
        DO_NOT_LOG() {
            @Override
            boolean allowsValueWith(Safety _valueSafety) {
                // do-not-log on a parameter isn't meaningful for callers, only for the implementation
                return true;
            }
        },
        UNSAFE() {
            @Override
            boolean allowsValueWith(Safety valueSafety) {
                // We allow safe data to be provided to an unsafe annotated parameter because that's safe, however
                // we should separately flag and prompt migration of such UnsafeArgs to SafeArg.
                return valueSafety != Safety.DO_NOT_LOG;
            }
        },
        SAFE() {
            @Override
            boolean allowsValueWith(Safety valueSafety) {
                return valueSafety == Safety.UNKNOWN || valueSafety == Safety.SAFE;
            }
        };

        abstract boolean allowsValueWith(Safety valueSafety);
    }
}
