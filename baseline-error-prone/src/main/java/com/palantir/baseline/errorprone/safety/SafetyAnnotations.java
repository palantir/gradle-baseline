/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone.safety;

import com.google.errorprone.VisitorState;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Optional;

public final class SafetyAnnotations {
    private static final String SAFE = "com.palantir.logsafe.Safe";
    private static final String UNSAFE = "com.palantir.logsafe.Unsafe";
    private static final String DO_NOT_LOG = "com.palantir.logsafe.DoNotLog";

    private static final Matcher<ExpressionTree> TO_STRING =
            MethodMatchers.instanceMethod().anyClass().named("toString").withNoParameters();

    public static Safety getSafety(ExpressionTree tree, VisitorState state) {
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
            Optional<Safety> maybeResult = getMethodInvocationResultSafety((MethodInvocationTree) argument, state);
            if (maybeResult.isPresent()) {
                return maybeResult.get();
            }
        }
        // Check the argument symbol itself:
        Symbol argumentSymbol = ASTHelpers.getSymbol(argument);
        return argumentSymbol != null ? getSafety(argumentSymbol, state) : Safety.UNKNOWN;
    }

    public static Optional<Safety> getMethodInvocationResultSafety(
            MethodInvocationTree argumentInvocation, VisitorState state) {
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(argumentInvocation);
        if (methodSymbol != null) {
            Safety methodSafety = getSafety(methodSymbol, state);
            // non-annotated toString inherits type-level safety.
            if (methodSafety == Safety.UNKNOWN && TO_STRING.matches(argumentInvocation, state)) {
                return Optional.of(getSafety(ASTHelpers.getReceiver(argumentInvocation), state));
            }
            return Optional.of(methodSafety);
        }
        return Optional.empty();
    }

    public static Safety getSafety(Symbol symbol, VisitorState state) {
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

    private SafetyAnnotations() {}
}
