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

package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.predicates.TypePredicate;
import com.google.errorprone.predicates.TypePredicates;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.function.Predicate;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Supplying an object which uses legacy javax types, such as javax.ws.rs to a\n"
                + "method which requires newer jakarta types is a runtime error. This check ensures\n"
                + "that you only supply proper types to these methods which generally just take an\n"
                + "untyped Object. There is no auto-fix for this check, you must fix it manually")
public final class ForbidJavaxParameterType extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final String FORBID_JAVAX_TYPE = "com.palantir.errorprone.ForbidJavax";

    private static final Predicate<Symbol> HAS_JAXRS_ANNOTATION =
            SymbolPredicates.hasAnnotationWithPackage("javax.ws.rs");

    private static final TypePredicate IMPLEMENTS_FEATURE = TypePredicates.isDescendantOf("javax.ws.rs.core.Feature");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        List<? extends ExpressionTree> arguments = tree.getArguments();
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        List<VarSymbol> methodParameters = methodSymbol.getParameters();

        if (methodParameters == null
                || methodParameters.isEmpty()
                || arguments == null
                || arguments.isEmpty()
                || arguments.size() != methodParameters.size()) {
            return Description.NO_MATCH;
        }

        for (int i = 0; i < methodParameters.size(); ++i) {
            VarSymbol parameter = methodParameters.get(i);
            ExpressionTree argument = arguments.get(i);

            boolean missingForbidJavaAnnotation = parameter.getAnnotationMirrors().stream()
                    .noneMatch(
                            c -> FORBID_JAVAX_TYPE.equals(c.type.tsym.flatName().toString()));
            if (missingForbidJavaAnnotation) {
                continue;
            }

            Type resultType = ASTHelpers.getResultType(argument);
            if (hasJavaxInclusions(resultType, state)) {
                // we know that resultType is not-null here since hasJavaxInclusions returns false for null
                return buildDescription(tree)
                        .setMessage(resultType.asElement().getQualifiedName().toString()
                                + " registers legacy javax imports but is being supplied to a method which"
                                + " requires jakarta")
                        .build();
            }
        }

        return Description.NO_MATCH;
    }

    private boolean hasJavaxInclusions(Type resultType, VisitorState state) {
        if (resultType == null) {
            return false;
        }

        return hasJavaxInclusionsOnType(resultType.asElement(), state);
    }

    private boolean hasJavaxInclusionsOnType(TypeSymbol symbol, VisitorState state) {
        if (symbol instanceof ClassSymbol) {
            ClassSymbol classType = (ClassSymbol) symbol;

            if (IMPLEMENTS_FEATURE.apply(classType.type, state)) {
                return true;
            }

            if (HAS_JAXRS_ANNOTATION.test(classType)) {
                return true;
            }

            // this obviously fails if there is > 1 level of hierarchy where the JaxRS
            // annotations exist. Fortunately this should be fine for most use cases
            List<Type> thisAndParents = ImmutableList.<Type>builder()
                    .add(classType.type)
                    .addAll(classType.getInterfaces())
                    .build();
            for (Type t : thisAndParents) {
                for (Symbol sym : t.tsym.getEnclosedElements()) {
                    if (HAS_JAXRS_ANNOTATION.test(sym)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
