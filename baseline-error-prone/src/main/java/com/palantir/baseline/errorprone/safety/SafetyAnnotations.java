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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public final class SafetyAnnotations {
    private static final String SAFE = "com.palantir.logsafe.Safe";
    private static final String UNSAFE = "com.palantir.logsafe.Unsafe";
    private static final String DO_NOT_LOG = "com.palantir.logsafe.DoNotLog";

    public static Safety getSafety(ExpressionTree input, VisitorState state) {
        // Check the result type
        ExpressionTree tree = ASTHelpers.stripParentheses(input);
        Safety resultTypeSafet = getResultTypeSafety(tree, state);

        // Check the argument symbol itself:
        Symbol argumentSymbol = ASTHelpers.getSymbol(tree);
        Safety symbolSafety = argumentSymbol != null ? getSafety(argumentSymbol, state) : Safety.UNKNOWN;
        return Safety.mergeAssumingUnknownIsSame(resultTypeSafet, symbolSafety);
    }

    public static Safety getSafety(@Nullable Symbol symbol, VisitorState state) {
        if (symbol != null) {
            if (ASTHelpers.hasAnnotation(symbol, DO_NOT_LOG, state)) {
                return Safety.DO_NOT_LOG;
            }
            if (ASTHelpers.hasAnnotation(symbol, UNSAFE, state)) {
                return Safety.UNSAFE;
            }
            if (ASTHelpers.hasAnnotation(symbol, SAFE, state)) {
                return Safety.SAFE;
            }
            // Check super-methods
            if (symbol instanceof MethodSymbol) {
                return getSuperMethodSafety((MethodSymbol) symbol, state);
            }
            if (symbol instanceof VarSymbol) {
                VarSymbol varSymbol = (VarSymbol) symbol;
                return getSuperMethodParameterSafety(varSymbol, state);
            }
        }
        return Safety.UNKNOWN;
    }

    private static Safety getSuperMethodSafety(MethodSymbol method, VisitorState state) {
        Safety safety = Safety.UNKNOWN;
        if (!method.isStaticOrInstanceInit()) {
            for (MethodSymbol superMethod : ASTHelpers.findSuperMethods(method, state.getTypes())) {
                safety = Safety.mergeAssumingUnknownIsSame(safety, getSafety(superMethod, state));
            }
        }
        return safety;
    }

    private static Safety getSuperMethodParameterSafety(VarSymbol varSymbol, VisitorState state) {
        Safety safety = Safety.UNKNOWN;
        if (varSymbol.owner instanceof MethodSymbol) {
            // If the owner is a MethodSymbol, this variable is a method parameter
            MethodSymbol method = (MethodSymbol) varSymbol.owner;
            if (!method.isStaticOrInstanceInit()) {
                List<VarSymbol> methodParameters = method.getParameters();
                for (int i = 0; i < methodParameters.size(); i++) {
                    VarSymbol current = methodParameters.get(i);
                    if (Objects.equals(current, varSymbol)) {
                        for (MethodSymbol superMethod : ASTHelpers.findSuperMethods(method, state.getTypes())) {
                            safety = Safety.mergeAssumingUnknownIsSame(
                                    safety,
                                    getSafety(superMethod.getParameters().get(i), state));
                        }
                        return safety;
                    }
                }
            }
        }
        return safety;
    }

    public static Safety getResultTypeSafety(ExpressionTree expression, VisitorState state) {
        Type resultType = ASTHelpers.getResultType(expression);
        return resultType == null ? Safety.UNKNOWN : getSafety(resultType.tsym, state);
    }

    private SafetyAnnotations() {}
}
