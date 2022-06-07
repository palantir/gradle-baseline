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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.MoreAnnotations;
import com.palantir.baseline.errorprone.safety.Safety;
import com.palantir.baseline.errorprone.safety.SafetyAnalysis;
import com.palantir.baseline.errorprone.safety.SafetyAnnotations;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.Name;
import java.util.List;
import javax.lang.model.element.Modifier;
import org.checkerframework.errorprone.javacutil.TreePathUtil;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        // This will be promoted after an initial rollout period
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Safe logging annotations should be propagated to encapsulating elements to allow static analysis "
                + "tooling to work with as much information as possible. This check can be auto-fixed using "
                + "`./gradlew classes testClasses -PerrorProneApply=SafeLoggingPropagation`")
public final class SafeLoggingPropagation extends BugChecker
        implements BugChecker.ClassTreeMatcher, BugChecker.MethodTreeMatcher {
    private static final Matcher<Tree> SAFETY_ANNOTATION_MATCHER = Matchers.anyOf(
            Matchers.isSameType(SafetyAnnotations.SAFE),
            Matchers.isSameType(SafetyAnnotations.UNSAFE),
            Matchers.isSameType(SafetyAnnotations.DO_NOT_LOG));

    private static final Matcher<MethodTree> TO_STRING = Matchers.allOf(
            Matchers.methodIsNamed("toString"),
            Matchers.methodHasNoParameters(),
            Matchers.not(Matchers.isStatic()),
            Matchers.methodReturns(Matchers.isSameType(String.class)));
    private static final Matcher<MethodTree> METHOD_RETURNS_VOID = Matchers.methodReturns(Matchers.isVoidType());
    private static final Matcher<MethodTree> NON_STATIC_NON_CTOR =
            Matchers.not(Matchers.anyOf(Matchers.hasModifier(Modifier.STATIC), Matchers.methodIsConstructor()));

    private static final Matcher<MethodTree> IS_IMMUTABLES_FIELD = Matchers.anyOf(
            Matchers.hasModifier(Modifier.ABSTRACT),
            Matchers.symbolHasAnnotation("org.immutables.value.Value.Default"),
            Matchers.symbolHasAnnotation("org.immutables.value.Value.Derived"),
            Matchers.symbolHasAnnotation("org.immutables.value.Value.Lazy"),
            Matchers.allOf(Matchers.hasModifier(Modifier.DEFAULT), Matchers.enclosingClass((Matcher<ClassTree>)
                    (classTree, state) -> {
                        ClassSymbol classSymbol = ASTHelpers.getSymbol(classTree);
                        return classSymbol != null && immutablesDefaultAsDefault(classSymbol, state);
                    })),
            (methodTree, state) -> hasJacksonAnnotation(ASTHelpers.getSymbol(methodTree), state));
    private static final Matcher<MethodTree> GETTER_METHOD_MATCHER = Matchers.anyOf(
            Matchers.allOf(
                    NON_STATIC_NON_CTOR,
                    Matchers.not(METHOD_RETURNS_VOID),
                    Matchers.methodHasNoParameters(),
                    IS_IMMUTABLES_FIELD),
            // Always include toString safety
            TO_STRING);

    private static final com.google.errorprone.suppliers.Supplier<Name> TO_STRING_NAME =
            VisitorState.memoize(state -> state.getName("toString"));

    private static final com.google.errorprone.suppliers.Supplier<Name> IMMUTABLES_STYLE =
            VisitorState.memoize(state -> state.getName("org.immutables.value.Value.Style"));

    private static final com.google.errorprone.suppliers.Supplier<Name> JACKSON_ANNOTATION =
            VisitorState.memoize(state -> state.getName("com.fasterxml.jackson.annotation.JacksonAnnotation"));

    @Override
    public Description matchClass(ClassTree classTree, VisitorState state) {
        ClassSymbol classSymbol = ASTHelpers.getSymbol(classTree);
        if (classSymbol == null || classSymbol.isAnonymous()) {
            return Description.NO_MATCH;
        }
        if (isRecord(classSymbol)) {
            return matchRecord(classTree, classSymbol, state);
        } else {
            return matchClassOrInterface(classTree, classSymbol, state);
        }
    }

    /** Matches any jackson annotation based on the meta-annotation {@code @JacksonAnnotation}. */
    private static boolean hasJacksonAnnotation(Symbol typeSymbol, VisitorState state) {
        return hasJacksonAnnotation(typeSymbol, state, 1)
                && !ASTHelpers.hasAnnotation(typeSymbol, "com.fasterxml.jackson.annotation.JsonIgnore", state);
    }

    private static boolean hasJacksonAnnotation(Symbol typeSymbol, VisitorState state, int nestedDepth) {
        if (typeSymbol != null) {
            Name jacksonAnnotationName = JACKSON_ANNOTATION.get(state);
            for (Attribute.Compound metadata : typeSymbol.getRawAttributes()) {
                if (jacksonAnnotationName.equals(metadata.type.tsym.getQualifiedName())) {
                    return true;
                }
                if (nestedDepth > 0 && hasJacksonAnnotation(metadata.type.tsym, state, nestedDepth - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean immutablesDefaultAsDefault(TypeSymbol typeSymbol, VisitorState state) {
        return immutablesDefaultAsDefault(typeSymbol, state, 1);
    }

    private static boolean immutablesDefaultAsDefault(TypeSymbol typeSymbol, VisitorState state, int nestedDepth) {
        Name styleName = IMMUTABLES_STYLE.get(state);
        for (Attribute.Compound metadata : typeSymbol.getRawAttributes()) {
            if (styleName.equals(metadata.type.tsym.getQualifiedName())) {
                return immutablesDefaultAsDefault(metadata);
            }
            if (nestedDepth > 0 && immutablesDefaultAsDefault(metadata.type.tsym, state, nestedDepth - 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean immutablesDefaultAsDefault(Attribute.Compound styleAnnotation) {
        return MoreAnnotations.getValue(styleAnnotation, "defaultAsDefault")
                .map(attr -> (boolean) attr.getValue())
                .orElse(false);
    }

    private static boolean isRecord(ClassSymbol classSymbol) {
        // Can use classSymbol.isRecord() in future versions
        return (classSymbol.flags() & 1L << 61) != 0;
    }

    @SuppressWarnings("unchecked")
    private static List<VarSymbol> getRecordComponents(ClassSymbol classSymbol) {
        // Can use classSymbol.getRecordComponents() in future versions
        try {
            return (List<VarSymbol>)
                    ClassSymbol.class.getMethod("getRecordComponents").invoke(classSymbol);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get record components", e);
        }
    }

    private Description matchRecord(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        Safety existingClassSafety = SafetyAnnotations.getSafety(classTree, state);
        Safety safety = SafetyAnnotations.getTypeSafetyFromAncestors(classTree, state);
        for (VarSymbol recordComponent : getRecordComponents(classSymbol)) {
            Safety symbolSafety = SafetyAnnotations.getSafety(recordComponent, state);
            Safety typeSymSafety = SafetyAnnotations.getSafety(recordComponent.type.tsym, state);
            Safety recordComponentSafety = Safety.mergeAssumingUnknownIsSame(symbolSafety, typeSymSafety);
            safety = safety.leastUpperBound(recordComponentSafety);
        }
        return handleSafety(classTree, classTree.getModifiers(), state, existingClassSafety, safety);
    }

    private Description matchClassOrInterface(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        if (ASTHelpers.hasAnnotation(classSymbol, "org.immutables.value.Value.Immutable", state)) {
            return matchImmutables(classTree, classSymbol, state);
        }
        return matchBasedOnToString(classTree, classSymbol, state);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private Description matchImmutables(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        Safety existingClassSafety = SafetyAnnotations.getSafety(classTree, state);
        Safety safety = SafetyAnnotations.getTypeSafetyFromAncestors(classTree, state);
        boolean hasKnownGetter = false;
        boolean isJson = hasJacksonAnnotation(classSymbol, state);
        for (Tree member : classTree.getMembers()) {
            if (member instanceof MethodTree) {
                MethodTree methodMember = (MethodTree) member;
                if (GETTER_METHOD_MATCHER.matches(methodMember, state)) {
                    MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodMember);
                    boolean redacted =
                            ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Redacted", state);
                    if (redacted && !isJson && !hasJacksonAnnotation(methodSymbol, state)) {
                        // Redacted fields can be ignored so long as the object is not json-serializable, in which
                        // case logging may occur using jackson rather than toString.
                        continue;
                    }
                    // The redaction check allows us to add @DoNotLog to redacted fields in the same sweep as
                    // adding class-level safety annotations. Otherwise, we would have to run the automatic
                    // fixes twice.
                    Safety getterSafety = SafetyAnnotations.getSafety(methodMember.getReturnType(), state);
                    if (redacted && (getterSafety == Safety.UNKNOWN || getterSafety == Safety.SAFE)) {
                        // unsafe data may be redacted, however we assume redaction means do-not-log by default
                        getterSafety = Safety.DO_NOT_LOG;
                    }
                    if (getterSafety != Safety.UNKNOWN) {
                        hasKnownGetter = true;
                    }
                    safety = safety.leastUpperBound(getterSafety);
                }
            }
        }
        // If no getter-style methods are detected, assume this is not a value type.
        if (!hasKnownGetter) {
            return Description.NO_MATCH;
        }
        return handleSafety(classTree, classTree.getModifiers(), state, existingClassSafety, safety);
    }

    private Description matchBasedOnToString(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        MethodSymbol toStringSymbol = ASTHelpers.resolveExistingMethod(
                state, classSymbol, TO_STRING_NAME.get(state), ImmutableList.of(), ImmutableList.of());
        if (toStringSymbol == null) {
            return Description.NO_MATCH;
        }
        Safety existingClassSafety = SafetyAnnotations.getSafety(classTree, state);
        Safety symbolSafety = SafetyAnnotations.getSafety(toStringSymbol, state);
        return handleSafety(classTree, classTree.getModifiers(), state, existingClassSafety, symbolSafety);
    }

    private Description handleSafety(
            Tree tree, ModifiersTree treeModifiers, VisitorState state, Safety existingSafety, Safety computedSafety) {
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
                return annotate(tree, treeModifiers, state, SafetyAnnotations.DO_NOT_LOG);
            case UNSAFE:
                return annotate(tree, treeModifiers, state, SafetyAnnotations.UNSAFE);
        }
        return Description.NO_MATCH;
    }

    private Description annotate(Tree tree, ModifiersTree treeModifiers, VisitorState state, String annotationName) {
        // Don't cause churn in test-code.
        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualifiedAnnotation = SuggestedFixes.qualifyType(state, fix, annotationName);
        for (AnnotationTree annotationTree : treeModifiers.getAnnotations()) {
            Tree annotationType = annotationTree.getAnnotationType();
            if (SAFETY_ANNOTATION_MATCHER.matches(annotationType, state)) {
                fix.replace(annotationTree, "");
            }
        }
        fix.prefixWith(tree, String.format("@%s ", qualifiedAnnotation));
        return buildDescription(tree).addFix(fix.build()).build();
    }

    @Override
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public Description matchMethod(MethodTree method, VisitorState state) {
        if (METHOD_RETURNS_VOID.matches(method, state) || method.getReturnType() == null) {
            return Description.NO_MATCH;
        }
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(method);
        // Removing this check may be helpful once we begin to use the 'var' keyword.
        if (methodSymbol.owner.isAnonymous()) {
            return Description.NO_MATCH;
        }
        Safety methodDeclaredSafety = Safety.mergeAssumingUnknownIsSame(
                SafetyAnnotations.getSafety(methodSymbol, state),
                SafetyAnnotations.getSafety(method.getReturnType(), state));
        // Redacted is do-not-log by default, but may be unsafe.
        if (methodDeclaredSafety != Safety.DO_NOT_LOG
                && methodDeclaredSafety != Safety.UNSAFE
                && ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Redacted", state)) {
            return handleSafety(method, method.getModifiers(), state, methodDeclaredSafety, Safety.DO_NOT_LOG);
        }
        if (methodDeclaredSafety != Safety.UNKNOWN) {
            // No need to verify, that's handled by 'IllegalSafeLoggingArgument'
            return Description.NO_MATCH;
        }
        if ((methodSymbol.flags() & Flags.ABSTRACT) != 0) {
            return Description.NO_MATCH;
        }
        // Don't cause churn in test-code. This is checked prior to the more expensive safety analysis
        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }
        Safety combinedReturnSafety = method.accept(new ReturnStatementSafetyScanner(method), state);
        if (combinedReturnSafety == null) {
            return Description.NO_MATCH;
        }
        return handleSafety(method, method.getModifiers(), state, methodDeclaredSafety, combinedReturnSafety);
    }

    private static final class ReturnStatementSafetyScanner extends TreeScanner<Safety, VisitorState> {

        private final MethodTree target;

        ReturnStatementSafetyScanner(MethodTree target) {
            this.target = target;
        }

        @Override
        public Safety visitReturn(ReturnTree node, VisitorState visitorState) {
            ExpressionTree expression = node.getExpression();
            if (expression == null) {
                return null;
            }
            // Validate that the discovered ReturnTree is from the same scope as the 'target' method.
            TreePath path = TreePath.getPath(visitorState.getPath().getCompilationUnit(), expression);
            if (target.equals(TreePathUtil.enclosingMethodOrLambda(path))) {
                return SafetyAnalysis.of(visitorState.withPath(path));
            } else {
                // Unclear what's happening in this case, so we definitely don't want to claim SAFE
                return Safety.UNKNOWN;
            }
        }

        // Don't search beyond the scope of the method
        @Override
        public Safety visitClass(ClassTree _node, VisitorState _obj) {
            return null;
        }

        @Override
        public Safety visitNewClass(NewClassTree node, VisitorState _state) {
            return null;
        }

        @Override
        public Safety visitLambdaExpression(LambdaExpressionTree node, VisitorState _state) {
            return null;
        }

        @Override
        public Safety reduce(Safety lhs, Safety rhs) {
            if (lhs == null) {
                return rhs;
            }
            if (rhs == null) {
                return lhs;
            }
            return lhs.leastUpperBound(rhs);
        }
    }
}
