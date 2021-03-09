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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.stream.BaseStream;

@AutoService(BugChecker.class)
@BugPattern(
        name = "AutoCloseableMustBeClosed",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = SeverityLevel.SUGGESTION,
        summary = "If a constructor or method returns an AutoCloseable, it should be annotated "
                + "@MustBeClosed to ensure callers appropriately close resources")
public final class AutoCloseableMustBeClosed extends BugChecker implements MethodTreeMatcher {

    private static final String MUST_BE_CLOSED_TYPE = "com.google.errorprone.annotations.MustBeClosed";
    private static final String CAN_IGNORE_RETURN_VALUE_TYPE = "com.google.errorprone.annotations.CanIgnoreReturnValue";

    private static final Matcher<MethodTree> methodReturnsAutoCloseable = Matchers.allOf(
            Matchers.not(Matchers.methodIsConstructor()),
            Matchers.methodReturns(Matchers.isSubtypeOf(AutoCloseable.class)),
            // ignore Stream for now, see https://errorprone.info/bugpattern/StreamResourceLeak
            Matchers.not(Matchers.methodReturns(Matchers.isSubtypeOf(BaseStream.class))),
            // Avoid noise when methods override base interfaces that aren't annotated. In most cases
            // the supertype reference is used, so annotations are less helpful on overrides.
            // The override must be annotated MustBeClosed if the supertype is annotated.
            Matchers.anyOf(
                    // It might be worthwhile to make an exception for generic types, for example
                    // Supplier<InputStream> subtypes should definitely be annotated.
                    Matchers.not(OverrideMethod.INSTANCE),
                    Matchers.hasAnnotationOnAnyOverriddenMethod(MUST_BE_CLOSED_TYPE)));

    private static final Matcher<MethodTree> constructsAutoCloseable = Matchers.allOf(
            Matchers.methodIsConstructor(),
            Matchers.enclosingClass(Matchers.isSubtypeOf(AutoCloseable.class)),
            // ignore Stream for now, see https://errorprone.info/bugpattern/StreamResourceLeak
            Matchers.not(Matchers.enclosingClass(Matchers.isSubtypeOf(BaseStream.class))));

    private static final Matcher<MethodTree> methodNotAnnotatedMustBeClosed =
            Matchers.not(Matchers.hasAnnotation(MUST_BE_CLOSED_TYPE));

    private static final Matcher<MethodTree> methodNotAnnotatedIgnoreReturnValue =
            Matchers.not(Matchers.hasAnnotation(CAN_IGNORE_RETURN_VALUE_TYPE));

    private static final Matcher<MethodTree> methodShouldBeAnnotatedMustBeClosed = Matchers.allOf(
            methodNotAnnotatedMustBeClosed,
            methodNotAnnotatedIgnoreReturnValue,
            Matchers.anyOf(methodReturnsAutoCloseable, constructsAutoCloseable));

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (methodShouldBeAnnotatedMustBeClosed.matches(tree, state)) {
            SuggestedFix.Builder builder = SuggestedFix.builder();
            String annotation = SuggestedFixes.qualifyType(state, builder, MUST_BE_CLOSED_TYPE);
            return buildDescription(tree)
                    .addFix(builder.prefixWith(tree, "@" + annotation + " ").build())
                    .build();
        }
        return Description.NO_MATCH;
    }

    private enum OverrideMethod implements Matcher<MethodTree> {
        INSTANCE;

        @Override
        public boolean matches(MethodTree tree, VisitorState state) {
            MethodSymbol methodSym = ASTHelpers.getSymbol(tree);
            if (methodSym == null) {
                return false;
            }
            return !ASTHelpers.findSuperMethods(methodSym, state.getTypes()).isEmpty();
        }
    }
}
