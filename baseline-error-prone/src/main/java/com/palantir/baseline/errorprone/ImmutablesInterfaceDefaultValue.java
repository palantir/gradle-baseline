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
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;

/**
 * Checks that interface default methods in an immutables.org @Value.Immutable are marked with @Value.Default.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "ImmutablesInterfaceDefaultValue",
        linkType = LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "@Value.Immutable interface default methods should be annotated @Value.Default")
public final class ImmutablesInterfaceDefaultValue extends BugChecker implements MethodTreeMatcher {

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        ClassSymbol enclosingClass = ASTHelpers.enclosingClass(ASTHelpers.getSymbol(tree));
        if (enclosingClass != null
                && ASTHelpers.hasAnnotation(enclosingClass, "org.immutables.value.Value.Immutable", state)) {
            MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
            if (methodSymbol != null
                    && methodSymbol.isDefault()
                    && !ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Default", state)
                    && !ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Derived", state)
                    && !ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Lazy", state)) {
                SuggestedFix.Builder builder = SuggestedFix.builder();
                String annotation = SuggestedFixes.qualifyType(state, builder, "org.immutables.value.Value.Default");
                return buildDescription(tree)
                        .addFix(builder.prefixWith(tree, "@" + annotation + " ").build())
                        .build();
            }
        }
        return Description.NO_MATCH;
    }
}
