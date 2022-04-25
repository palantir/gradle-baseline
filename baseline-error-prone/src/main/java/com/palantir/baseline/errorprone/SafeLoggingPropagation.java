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
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.baseline.errorprone.safety.Safety;
import com.palantir.baseline.errorprone.safety.SafetyAnnotations;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        // This will be promoted after an initial rollout period
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Safe logging annotations should be propagated to encapsulating elements to allow static analysis "
                + "tooling to work with as much information as possible. This check can be auto-fixed using "
                + "`./gradlew classes testClasses -PerrorProneApply=SafeLoggingPropagation`")
public final class SafeLoggingPropagation extends BugChecker implements BugChecker.ClassTreeMatcher {
    private static final Matcher<Tree> SAFETY_ANNOTATION_MATCHER = Matchers.anyOf(
            Matchers.isSameType(SafetyAnnotations.SAFE),
            Matchers.isSameType(SafetyAnnotations.UNSAFE),
            Matchers.isSameType(SafetyAnnotations.DO_NOT_LOG));

    private static final Matcher<MethodTree> NON_STATIC_METHOD_MATCHER =
            Matchers.not(Matchers.hasModifier(Modifier.STATIC));
    private static final Matcher<MethodTree> GETTER_METHOD_MATCHER = Matchers.allOf(
            Matchers.not(Matchers.methodReturns(Matchers.isVoidType())), Matchers.methodHasNoParameters());

    @Override
    public Description matchClass(ClassTree classTree, VisitorState state) {
        ClassSymbol classSymbol = ASTHelpers.getSymbol(classTree);
        if (classSymbol == null || classSymbol.isAnonymous()) {
            return Description.NO_MATCH;
        }
        Safety existingClassSafety = SafetyAnnotations.getSafety(classTree, state);
        Safety safety = SafetyAnnotations.getSafety(classTree.getExtendsClause(), state);
        for (Tree implemented : classTree.getImplementsClause()) {
            safety = safety.leastUpperBound(SafetyAnnotations.getSafety(implemented, state));
        }
        boolean hasNonGetterMethod = false;
        boolean hasKnownGetter = false;
        for (Tree member : classTree.getMembers()) {
            if (member instanceof MethodTree) {
                MethodTree methodMember = (MethodTree) member;
                if (NON_STATIC_METHOD_MATCHER.matches(methodMember, state)) {
                    if (GETTER_METHOD_MATCHER.matches(methodMember, state)) {
                        Safety getterSafety = SafetyAnnotations.getSafety(methodMember.getReturnType(), state);
                        if (getterSafety != Safety.UNKNOWN) {
                            hasKnownGetter = true;
                        }
                        safety = safety.leastUpperBound(getterSafety);
                    } else {
                        hasNonGetterMethod = true;
                    }
                }
            }
        }
        // If no getter-style methods are detected, assume this is not a value type.
        if (!hasKnownGetter) {
            return Description.NO_MATCH;
        }
        // non-getter methods are avoided such that we don't annotate non-value types unless we have additional
        // data to suggest this is a value (immutables annotations).
        if (hasNonGetterMethod
                && !ASTHelpers.hasAnnotation(classSymbol, "org.immutables.value.Value.Immutable", state)) {
            return Description.NO_MATCH;
        }

        return handleSafety(classTree, state, existingClassSafety, safety);
    }

    private Description handleSafety(
            ClassTree classTree, VisitorState state, Safety existingSafety, Safety computedSafety) {
        if (existingSafety != Safety.UNKNOWN && existingSafety.allowsValueWith(computedSafety)) {
            // Do not suggest promotion, this check is not exhaustive.
            return Description.NO_MATCH;
        }
        switch (computedSafety) {
            case UNKNOWN:
                // Nothing to do
                return Description.NO_MATCH;
            case SAFE:
                // Do not suggest promotion to safe, this check is not exhaustive.
                return Description.NO_MATCH;
            case DO_NOT_LOG:
                return annotate(classTree, state, SafetyAnnotations.DO_NOT_LOG);
            case UNSAFE:
                return annotate(classTree, state, SafetyAnnotations.UNSAFE);
        }
        return Description.NO_MATCH;
    }

    private Description annotate(ClassTree classTree, VisitorState state, String annotationName) {
        // Don't cause churn in test-code.
        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualifiedAnnotation = SuggestedFixes.qualifyType(state, fix, annotationName);
        for (AnnotationTree annotationTree : classTree.getModifiers().getAnnotations()) {
            Tree annotationType = annotationTree.getAnnotationType();
            if (SAFETY_ANNOTATION_MATCHER.matches(annotationType, state)) {
                fix.replace(annotationTree, "");
            }
        }
        fix.prefixWith(classTree, String.format("@%s ", qualifiedAnnotation));
        return buildDescription(classTree).addFix(fix.build()).build();
    }
}
