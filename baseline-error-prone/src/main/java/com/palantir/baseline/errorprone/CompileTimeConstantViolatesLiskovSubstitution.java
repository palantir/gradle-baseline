/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.Set;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        name = "CompileTimeConstantViolatesLiskovSubstitution",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "@CompileTimeConstant annotations on method parameters must also be applied to the super method. "
                + "Similarly, if a superclass or superinterface is annotated, implementations must also be annotated.")
public final class CompileTimeConstantViolatesLiskovSubstitution extends BugChecker
        implements BugChecker.MethodTreeMatcher {

    private static final Matcher<MethodTree> INEXPENSIVE_CHECK = Matchers.anyOf(
            Matchers.methodIsConstructor(),
            Matchers.hasModifier(Modifier.STATIC),
            Matchers.hasModifier(Modifier.PRIVATE));

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (INEXPENSIVE_CHECK.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        Set<MethodSymbol> superMethods = ASTHelpers.findSuperMethods(methodSymbol, state.getTypes());
        // no super-methods, nothing to do
        if (superMethods.isEmpty()) {
            return Description.NO_MATCH;
        }

        int parameterIndex = -1;
        for (VarSymbol parameter : methodSymbol.getParameters()) {
            ++parameterIndex;

            if (ASTHelpers.hasAnnotation(parameter, CompileTimeConstant.class, state)) {
                if (anySuperMethodsMissingParameterAnnotation(superMethods, parameterIndex, state)) {
                    state.reportMatch(buildDescription(tree.getParameters().get(parameterIndex))
                            .setMessage("@CompileTimeConstant annotations on method parameters "
                                    + "must also be applied to the super method otherwise non-constant values "
                                    + "will be allowed based on the reference variable type.")
                            .build());
                }
            } else if (anySuperMethodsHaveParameterAnnotation(superMethods, parameterIndex, state)) {
                SuggestedFix.Builder fix = SuggestedFix.builder();
                VariableTree parameterTree = tree.getParameters().get(parameterIndex);
                fix.prefixWith(
                        parameterTree,
                        String.format(
                                "@%s ", SuggestedFixes.qualifyType(state, fix, CompileTimeConstant.class.getName())));
                state.reportMatch(buildDescription(parameterTree)
                        .setMessage("When a superclass or superinterface is annotated with "
                                + "@CompileTimeConstant, implementations must also be annotated "
                                + "otherwise non-constant values will be allowed based on the "
                                + "reference variable type.")
                        .addFix(fix.build())
                        .build());
            }
        }

        return Description.NO_MATCH;
    }

    private boolean anySuperMethodsMissingParameterAnnotation(
            Set<MethodSymbol> superMethods, int parameterIndex, VisitorState state) {
        for (MethodSymbol superMethod : superMethods) {
            VarSymbol parameter = superMethod.getParameters().get(parameterIndex);
            if (!ASTHelpers.hasAnnotation(parameter, CompileTimeConstant.class, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean anySuperMethodsHaveParameterAnnotation(
            Set<MethodSymbol> superMethods, int parameterIndex, VisitorState state) {
        for (MethodSymbol superMethod : superMethods) {
            VarSymbol parameter = superMethod.getParameters().get(parameterIndex);
            if (ASTHelpers.hasAnnotation(parameter, CompileTimeConstant.class, state)) {
                return true;
            }
        }
        return false;
    }
}
