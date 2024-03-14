/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.versioning;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Alters code that implements server-side conjure endpoints that are about to be deleted. It removes"
            + " the Override annotation and adds in a RemoveLater one. This check can be auto-fixed using `./gradlew"
            + " classes testClasses -PerrorProneApply=DeprecatedEndpointImplRemoval`")
public class DeprecatedEndpointImplRemoval extends BugChecker implements MethodTreeMatcher {

    private static final Matcher<AnnotationTree> OVERRIDE_MATCHER = Matchers.isSameType("java.lang.Override");

    private static final String CONJURE_SERVER_ENDPOINT =
            "com.palantir.conjure.java.lib.internal.ConjureServerEndpoint";
    public static final String REMOVE_LATER = "com.palantir.conjure.RemoveLater";

    @Override
    public Description matchMethod(MethodTree methodTree, VisitorState state) {
        if (!overridesDeprecatedForRemovalEndpoint(methodTree, state)) {
            return Description.NO_MATCH;
        }

        return applyFix(methodTree, methodTree.getModifiers(), state);
    }

    private static boolean overridesDeprecatedForRemovalEndpoint(MethodTree tree, VisitorState state) {
        MethodSymbol methodSym = ASTHelpers.getSymbol(tree);
        if (methodSym == null) {
            return false;
        }
        if (!ASTHelpers.hasAnnotation(methodSym, "java.lang.Override", state)) {
            return false;
        }
        // Check if this method implements a conjure server endpoint that is deprecated for removal.
        for (MethodSymbol method : ASTHelpers.findSuperMethods(methodSym, state.getTypes())) {
            Deprecated annotation = method.getAnnotation(Deprecated.class);
            if (annotation != null
                    && annotation.forRemoval()
                    && ASTHelpers.hasAnnotation(method, CONJURE_SERVER_ENDPOINT, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove the @Override annotation and add a @RemoveLater annotation.
     */
    private Description applyFix(Tree tree, ModifiersTree treeModifiers, VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualifiedAnnotation = SuggestedFixes.qualifyType(state, fix, REMOVE_LATER);
        for (AnnotationTree annotationTree : treeModifiers.getAnnotations()) {
            if (OVERRIDE_MATCHER.matches(annotationTree, state)) {
                fix.replace(annotationTree, "");
            }
        }
        fix.setShortDescription("Remove @Override and add @RemoveLater annotation");
        fix.prefixWith(tree, String.format("@%s ", qualifiedAnnotation));
        return buildDescription(tree).addFix(fix.build()).build();
    }
}
