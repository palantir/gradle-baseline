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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.predicates.TypePredicate;
import com.google.errorprone.predicates.TypePredicates;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * {@link PreferAssertj} provides an automated path from legacy test libraries to AssertJ. Our goal is to migrate
 * existing assertions to AssertJ without losing any information. It's an explicit non-goal to take advantage
 * of better assertions provided by AssertJ, because those should be implemented in such a way to improve poor
 * uses of AssertJ as well, which may run after the suggested fixes provided by this checker.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "PreferAssertj",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Prefer AssertJ fluent assertions")
public final class PreferAssertj extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final ImmutableSet<String> LEGACY_ASSERT_CLASSES = ImmutableSet.of(
            "org.hamcrest.MatcherAssert",
            "org.junit.Assert",
            "junit.framework.TestCase",
            "junit.framework.Assert");

    private static final Matcher<ExpressionTree> ASSERT_TRUE =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertTrue")
                    .withParameters(boolean.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_TRUE_DESCRIPTION =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .namedAnyOf(
                            "assertTrue",
                            // org.hamcrest.MatcherAssert.assertThat(String,boolean) is an assertTrue
                            "assertThat")
                    .withParameters(String.class.getName(), boolean.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_FALSE =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertFalse")
                    .withParameters(boolean.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_FALSE_DESCRIPTION =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertFalse")
                    .withParameters(String.class.getName(), boolean.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_NULL =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNull")
                    .withParameters(Object.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_NULL_DESCRIPTION =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNull")
                    .withParameters(String.class.getName(), Object.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_NOT_NULL =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotNull")
                    .withParameters(Object.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_NOT_NULL_DESCRIPTION =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotNull")
                    .withParameters(String.class.getName(), Object.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_SAME =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertSame")
                    .withParameters(Object.class.getName(), Object.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_SAME_DESCRIPTION =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertSame")
                    .withParameters(String.class.getName(), Object.class.getName(), Object.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_NOT_SAME =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotSame")
                    .withParameters(Object.class.getName(), Object.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_NOT_SAME_DESCRIPTION =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotSame")
                    .withParameters(String.class.getName(), Object.class.getName(), Object.class.getName());

    private static final Matcher<ExpressionTree> FAIL =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("fail")
                    .withParameters();

    private static final Matcher<ExpressionTree> FAIL_DESCRIPTION =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("fail")
                    .withParameters(String.class.getName());

    private static final Matcher<ExpressionTree> ASSERT_EQUALS_FLOATING = Matchers.anyOf(
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertEquals")
                    .withParameters("double", "double", "double"),
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertEquals")
                    .withParameters("float", "float", "float"));

    private static final Matcher<ExpressionTree> ASSERT_EQUALS_FLOATING_DESCRIPTION = Matchers.anyOf(
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertEquals")
                    .withParameters(String.class.getName(), "double", "double", "double"),
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertEquals")
                    .withParameters(String.class.getName(), "float", "float", "float"));

    private static final Matcher<ExpressionTree> ASSERT_NOT_EQUALS_FLOATING = Matchers.anyOf(
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotEquals")
                    .withParameters("double", "double", "double"),
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotEquals")
                    .withParameters("float", "float", "float"));

    private static final Matcher<ExpressionTree> ASSERT_NOT_EQUALS_FLOATING_DESCRIPTION = Matchers.anyOf(
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotEquals")
                    .withParameters(String.class.getName(), "double", "double", "double"),
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .named("assertNotEquals")
                    .withParameters(String.class.getName(), "float", "float", "float"));

    private static final Matcher<ExpressionTree> ASSERT_THAT = MethodMatchers.staticMethod()
            .onClassAny(LEGACY_ASSERT_CLASSES)
            .named("assertThat")
            .withParameters(Object.class.getName(), "org.hamcrest.Matcher");

    private static final Matcher<ExpressionTree> ASSERT_THAT_DESCRIPTION = MethodMatchers.staticMethod()
            .onClassAny(LEGACY_ASSERT_CLASSES)
            .named("assertThat")
            .withParameters(String.class.getName(), Object.class.getName(), "org.hamcrest.Matcher");

    // Does not match specific patterns, this handles all overloads of both assertEquals and assertArrayEquals.
    // Order is important, more specific (e.g. floating point) checks must execute first.
    private static final Matcher<ExpressionTree> ASSERT_EQUALS_CATCHALL =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    .namedAnyOf("assertEquals", "assertArrayEquals");

    private static final Matcher<ExpressionTree> ASSERT_NOT_EQUALS_CATCHALL =
            MethodMatchers.staticMethod()
                    .onClassAny(LEGACY_ASSERT_CLASSES)
                    // There is no Assert.assertArrayNotEquals
                    .named("assertNotEquals");

    @Override
    @SuppressWarnings({"CyclomaticComplexity", "MethodLength"})
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (ASSERT_TRUE.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 0) + ").isTrue()"));
        }
        if (ASSERT_TRUE_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                            + ").describedAs(" + argSource(tree, state, 0) + ").isTrue()"));
        }
        if (ASSERT_FALSE.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 0) + ").isFalse()"));
        }
        if (ASSERT_FALSE_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                            + ").describedAs(" + argSource(tree, state, 0) + ").isFalse()"));
        }
        if (ASSERT_NULL.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 0) + ").isNull()"));
        }
        if (ASSERT_NULL_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                            + ").describedAs(" + argSource(tree, state, 0) + ").isNull()"));
        }
        if (ASSERT_NOT_NULL.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 0) + ").isNotNull()"));
        }
        if (ASSERT_NOT_NULL_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                            + ").describedAs(" + argSource(tree, state, 0) + ").isNotNull()"));
        }
        if (ASSERT_SAME.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                            + ").isSameAs(" + argSource(tree, state, 0) + ")"));
        }
        if (ASSERT_SAME_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 2)
                            + ").describedAs(" + argSource(tree, state, 0) + ").isSameAs("
                            + argSource(tree, state, 1) + ")"));
        }
        if (ASSERT_NOT_SAME.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                            + ").isNotSameAs(" + argSource(tree, state, 0) + ")"));
        }
        if (ASSERT_NOT_SAME_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 2)
                            + ").describedAs(" + argSource(tree, state, 0) + ").isNotSameAs("
                            + argSource(tree, state, 1) + ")"));
        }
        if (FAIL_DESCRIPTION.matches(tree, state) || FAIL.matches(tree, state)) {
            return buildDescription(tree)
                    .addFix(SuggestedFix.builder()
                            .removeStaticImport("org.junit.Assert.fail")
                            .addStaticImport("org.assertj.core.api.Assertions.fail")
                            .replace(tree, "fail(" + (tree.getArguments().isEmpty()
                                    ? "\"fail\""
                                    : argSource(tree, state, 0)) + ")")
                            .build())
                    .build();

        }
        if (ASSERT_EQUALS_FLOATING.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) -> fix
                    .addStaticImport("org.assertj.core.api.Assertions.within")
                    .replace(tree, String.format("%s(%s)%s",
                            assertThat,
                            argSource(tree, state, 1),
                            isConstantZero(tree.getArguments().get(2))
                                    ? String.format(".isEqualTo(%s)", argSource(tree, state, 0))
                                    : String.format(".isCloseTo(%s, within(%s))",
                                    argSource(tree, state, 0), argSource(tree, state, 2)))));
        }
        if (ASSERT_EQUALS_FLOATING_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) -> fix
                    .addStaticImport("org.assertj.core.api.Assertions.within")
                    .replace(tree, String.format("%s(%s).describedAs(%s)%s",
                            assertThat,
                            argSource(tree, state, 2),
                            argSource(tree, state, 0),
                            isConstantZero(tree.getArguments().get(3))
                                    ? String.format(".isEqualTo(%s)", argSource(tree, state, 1))
                                    : String.format(".isCloseTo(%s, within(%s))",
                                    argSource(tree, state, 1), argSource(tree, state, 3)))));
        }
        if (ASSERT_NOT_EQUALS_FLOATING.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) -> fix
                    .addStaticImport("org.assertj.core.api.Assertions.within")
                    .replace(tree, String.format("%s(%s)%s",
                            assertThat,
                            argSource(tree, state, 1),
                            isConstantZero(tree.getArguments().get(2))
                                    ? String.format(".isNotEqualTo(%s)", argSource(tree, state, 0))
                                    : String.format(".isNotCloseTo(%s, within(%s))",
                                    argSource(tree, state, 0), argSource(tree, state, 2)))));
        }
        if (ASSERT_NOT_EQUALS_FLOATING_DESCRIPTION.matches(tree, state)) {
            return withAssertThat(tree, state, (assertThat, fix) -> fix
                    .addStaticImport("org.assertj.core.api.Assertions.within")
                    .replace(tree, String.format("%s(%s).describedAs(%s)%s",
                            assertThat,
                            argSource(tree, state, 2),
                            argSource(tree, state, 0),
                            isConstantZero(tree.getArguments().get(3))
                                    ? String.format(".isNotEqualTo(%s)", argSource(tree, state, 1))
                                    : String.format(".isNotCloseTo(%s, within(%s))",
                                    argSource(tree, state, 1), argSource(tree, state, 3)))));
        }
        if (ASSERT_THAT.matches(tree, state)) {
            Optional<String> replacement = tree.getArguments().get(1)
                    .accept(HamcrestVisitor.INSTANCE, state);
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 0) + ")"
                            + replacement.orElseGet(() ->
                            ".is(new "
                            + SuggestedFixes.qualifyType(state, fix, "org.assertj.core.api.HamcrestCondition")
                            + "<>("
                            + argSource(tree, state, 1) + "))")));
        }
        if (ASSERT_THAT_DESCRIPTION.matches(tree, state)) {
            Optional<String> replacement = tree.getArguments().get(2)
                    .accept(HamcrestVisitor.INSTANCE, state);
            return withAssertThat(tree, state, (assertThat, fix) ->
                    fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                            + ").describedAs(" + argSource(tree, state, 0) + ")"
                            + replacement.orElseGet(() -> ".is(new "
                            + SuggestedFixes.qualifyType(state, fix, "org.assertj.core.api.HamcrestCondition")
                            + "<>(" + argSource(tree, state, 2) + "))")));
        }
        if (ASSERT_EQUALS_CATCHALL.matches(tree, state)) {
            int parameters = tree.getArguments().size();
            if (parameters == 2) {
                return withAssertThat(tree, state, (assertThat, fix) ->
                        fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                                + ").isEqualTo(" + argSource(tree, state, 0) + ")"));
            } else if (parameters == 3 && ASTHelpers.isSameType(
                    ASTHelpers.getType(tree.getArguments().get(0)),
                    state.getTypeFromString(String.class.getName()),
                    state)) {
                return withAssertThat(tree, state, (assertThat, fix) ->
                        fix.replace(tree, assertThat + "(" + argSource(tree, state, 2)
                                + ").describedAs(" + argSource(tree, state, 0) + ").isEqualTo("
                                + argSource(tree, state, 1) + ")"));
            } else {
                // Does not fix assertArrayEquals(double[], double[], double)
                // or assertArrayEquals(float[], float[], float)
                return describeMatch(tree);
            }
        }
        if (ASSERT_NOT_EQUALS_CATCHALL.matches(tree, state)) {
            int parameters = tree.getArguments().size();
            if (parameters == 2) {
                return withAssertThat(tree, state, (assertThat, fix) ->
                        fix.replace(tree, assertThat + "(" + argSource(tree, state, 1)
                                + ").isNotEqualTo(" + argSource(tree, state, 0) + ")"));
            } else if (parameters == 3 && ASTHelpers.isSameType(
                    ASTHelpers.getType(tree.getArguments().get(0)),
                    state.getTypeFromString(String.class.getName()),
                    state)) {
                return withAssertThat(tree, state, (assertThat, fix) ->
                        fix.replace(tree, assertThat + "(" + argSource(tree, state, 2)
                                + ").describedAs(" + argSource(tree, state, 0) + ").isNotEqualTo("
                                + argSource(tree, state, 1) + ")"));
            } else {
                // I'm not aware of anything that should hit this.
                return describeMatch(tree);
            }
        }
        return Description.NO_MATCH;
    }

    /**
     * Provides a qualified 'assertThat' name. We attempt to use a static import if possible, otherwise fall back
     * to as qualified as possible.
     */
    private Description withAssertThat(
            MethodInvocationTree tree,
            VisitorState state,
            BiConsumer<String, SuggestedFix.Builder> assertThat) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualified;
        if (useStaticAssertjImport(state)) {
            fix.removeStaticImport("org.junit.Assert.assertThat")
                    .removeStaticImport("org.hamcrest.MatcherAssert.assertThat")
                    .addStaticImport("org.assertj.core.api.Assertions.assertThat");
            qualified = "assertThat";
        } else {
            qualified = SuggestedFixes.qualifyType(state, fix, "org.assertj.core.api.Assertions.assertThat");
        }
        assertThat.accept(qualified, fix);
        return buildDescription(tree)
                .addFix(fix.build())
                .build();
    }

    private static boolean useStaticAssertjImport(VisitorState state) {
        for (ImportTree importTree : state.getPath().getCompilationUnit().getImports()) {
            if (!importTree.isStatic()) {
                continue;
            }
            Tree identifier = importTree.getQualifiedIdentifier();
            if (!(identifier instanceof MemberSelectTree)) {
                continue;
            }
            MemberSelectTree memberSelectTree = (MemberSelectTree) identifier;
            if (!memberSelectTree.getIdentifier().contentEquals("assertThat")) {
                continue;
            }
            // if an 'assertThat' is already imported, and it's neither assertj nor org.junit.assert, use
            // the qualified name.
            return isExpressionSameType(state, memberSelectTree, "org.assertj.core.api.Assertions")
                    // if an 'assertThat' is already imported, and it's from (known) legacy Assert,
                    // we remove the static import and add assertj.
                    || isExpressionSameType(state, memberSelectTree, "org.junit.Assert")
                    || isExpressionSameType(state, memberSelectTree, "org.hamcrest.MatcherAssert");
        }
        // If we did not encounter an assertThat static import, we can import and use it.
        return true;
    }

    private static boolean isConstantZero(Tree tree) {
        Object constantValue = ASTHelpers.constValue(tree);
        return constantValue instanceof Number && ((Number) constantValue).doubleValue() == 0D;
    }

    private static boolean isExpressionSameType(VisitorState state, MemberSelectTree memberSelectTree, String type) {
        return ASTHelpers.isSameType(
                ASTHelpers.getType(memberSelectTree.getExpression()),
                state.getTypeFromString(type),
                state);
    }

    private static String argSource(MethodInvocationTree invocation, VisitorState state, int index) {
        checkArgument(index >= 0, "Index must be non-negative");
        List<? extends ExpressionTree> arguments = checkNotNull(invocation, "MethodInvocationTree").getArguments();
        checkArgument(index < arguments.size(), "Index is out of bounds");
        return checkNotNull(state.getSourceForNode(arguments.get(index)), "Failed to find argument source");
    }

    private static final class HamcrestVisitor extends SimpleTreeVisitor<Optional<String>, VisitorState> {
        private static final HamcrestVisitor INSTANCE = new HamcrestVisitor(false);

        private static final HamcrestVisitor NEGATED = new HamcrestVisitor(true);

        private static final TypePredicate MATCHERS = new TypePredicate() {

            private final TypePredicate matcherPredicate = TypePredicates.isDescendantOf("org.hamcrest.Matcher");
            private final TypePredicate[] predicates = new TypePredicate[] {
                    TypePredicates.isExactType("org.hamcrest.Matchers"),
                    TypePredicates.isExactType("org.hamcrest.CoreMatchers"),
                    // Allows uses of direct imports to be migrated as well,
                    // e.g. 'org.hamcrest.core.Is.is'.
                    (TypePredicate) (type, state) -> matcherPredicate.apply(type, state)
                            // Limit to Hamcrest packages to avoid interaction with non-standard library code
                            && type.toString().startsWith("org.hamcrest.")
            };

            @Override
            public boolean apply(Type type, VisitorState state) {
                for (TypePredicate predicate : predicates) {
                    if (predicate.apply(type, state)) {
                        return true;
                    }
                }
                return false;
            }
        };

        private static final Matcher<ExpressionTree> IS_MATCHER = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .named("is")
                .withParameters("org.hamcrest.Matcher");

        private static final Matcher<ExpressionTree> NOT_MATCHER = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .named("not")
                .withParameters("org.hamcrest.Matcher");

        private static final Matcher<ExpressionTree> EQUALS = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .namedAnyOf("is", "equalTo", "equalToObject")
                .withParameters(Object.class.getName());

        private static final Matcher<ExpressionTree> INSTANCE_OF = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .namedAnyOf("isA", "instanceOf")
                .withParameters("java.lang.Class");

        private static final Matcher<ExpressionTree> NULL = Matchers.anyOf(
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .named("nullValue")
                        .withParameters(),
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .named("nullValue")
                        .withParameters("java.lang.Class"));

        private static final Matcher<ExpressionTree> NOT_NULL = Matchers.anyOf(
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .named("notNullValue")
                        .withParameters(),
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .named("notNullValue")
                        .withParameters("java.lang.Class"));

        private static final Matcher<ExpressionTree> CONTAINS = Matchers.anyOf(
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .namedAnyOf("hasItem", "hasItemInArray")
                        .withParameters(Object.class.getName()),
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .named("containsString")
                        .withParameters(String.class.getName()));

        // Note: cannot match array/vararg arguments
        private static final Matcher<ExpressionTree> HAS_ITEMS = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .named("hasItems");

        private static final Matcher<ExpressionTree> CONTAINS_EXACTLY_ANY_ORDER = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .namedAnyOf("arrayContainingInAnyOrder", "containsInAnyOrder");

        private static final Matcher<ExpressionTree> CONTAINS_EXACTLY_ORDERED = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .named("contains");

        private static final Matcher<ExpressionTree> IS_EMPTY = Matchers.anyOf(
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .namedAnyOf("empty", "emptyIterable", "emptyArray", "anEmptyMap")
                        .withParameters(),
                MethodMatchers.staticMethod()
                        .onClass(MATCHERS)
                        .namedAnyOf("emptyCollectionOf", "emptyIterableOf")
                        .withParameters(Class.class.getName()));

        private static final Matcher<ExpressionTree> HAS_SIZE = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .namedAnyOf("hasSize", "aMapWithSize", "arrayWithSize", "iterableWithSize")
                .withParameters(int.class.getName());

        private static final Matcher<ExpressionTree> SAME_INSTANCE = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .namedAnyOf("sameInstance", "theInstance")
                .withParameters(Object.class.getName());

        private static final Matcher<ExpressionTree> STARTS_WITH = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .namedAnyOf("startsWith")
                .withParameters(String.class.getName());

        private static final Matcher<ExpressionTree> ENDS_WITH = MethodMatchers.staticMethod()
                .onClass(MATCHERS)
                .namedAnyOf("endsWith")
                .withParameters(String.class.getName());

        private final boolean negated;

        private HamcrestVisitor(boolean negated) {
            super(Optional.empty());
            this.negated = negated;
        }

        @Override
        @SuppressWarnings("CyclomaticComplexity")
        public Optional<String> visitMethodInvocation(MethodInvocationTree node, VisitorState state) {
            // is(Matcher) is for readability, fall through to the next matcher
            if (IS_MATCHER.matches(node, state)) {
                return node.getArguments().get(0).accept(this, state);
            }
            if (NOT_MATCHER.matches(node, state)) {
                return node.getArguments().get(0).accept(this.negated ? INSTANCE : NEGATED, state);
            }
            if (EQUALS.matches(node, state)) {
                return Optional.of((negated ? ".isNotEqualTo(" : ".isEqualTo(")
                        + argSource(node, state, 0) + ")");
            }
            if (INSTANCE_OF.matches(node, state)) {
                return Optional.of((negated ? ".isNotInstanceOf(" : ".isInstanceOf(")
                        + argSource(node, state, 0) + ")");
            }
            if (NULL.matches(node, state)) {
                return Optional.of(negated ? ".isNotNull()" : ".isNull()");
            }
            if (NOT_NULL.matches(node, state)) {
                return Optional.of(negated ? ".isNull()" : ".isNotNull()");
            }
            if (CONTAINS.matches(node, state)) {
                return Optional.of((negated ? ".doesNotContain(" : ".contains(") + argSource(node, state, 0) + ')');
            }
            if (IS_EMPTY.matches(node, state)) {
                return Optional.of(negated ? ".isNotEmpty()" : ".isEmpty()");
            }
            if (SAME_INSTANCE.matches(node, state)) {
                return Optional.of((negated ? ".isNotSameAs(" : ".isSameAs(") + argSource(node, state, 0) + ')');
            }
            if (STARTS_WITH.matches(node, state)) {
                return Optional.of((negated ? ".doesNotStartWith(" : ".startsWith(") + argSource(node, state, 0) + ')');
            }
            if (ENDS_WITH.matches(node, state)) {
                return Optional.of((negated ? ".doesNotEndWith(" : ".endsWith(") + argSource(node, state, 0) + ')');
            }
            if (HAS_SIZE.matches(node, state)) {
                if (negated) {
                    // No top level method for negated size assertions
                    return Optional.empty();
                }

                return Optional.of(".hasSize(" + argSource(node, state, 0) + ')');
            }
            if (HAS_ITEMS.matches(node, state) && isObjectVarArgs(node, state)) {
                if (negated) {
                    // this negates to 'doesNotContainAll' which doesn't exist. AssertJ doesNotContain
                    // evaluates as 'does not contain any'.
                    return Optional.empty();
                }
                return Optional.of(".contains(" + node.getArguments().stream()
                        .map(state::getSourceForNode)
                        .collect(Collectors.joining(", ")) + ')');
            }
            if (CONTAINS_EXACTLY_ANY_ORDER.matches(node, state) && isObjectVarArgs(node, state)) {
                if (negated) {
                    // this negates to 'doesNotContainExactlyInAnyOrder' which doesn't exist.
                    return Optional.empty();
                }
                return Optional.of(".containsExactlyInAnyOrder(" + node.getArguments().stream()
                        .map(state::getSourceForNode)
                        .collect(Collectors.joining(", ")) + ')');
            }
            if (CONTAINS_EXACTLY_ORDERED.matches(node, state) && isObjectVarArgs(node, state)) {
                if (negated) {
                    // this negates to 'doesNotContainExactly' which doesn't exist.
                    return Optional.empty();
                }
                return Optional.of(".containsExactly(" + node.getArguments().stream()
                        .map(state::getSourceForNode)
                        .collect(Collectors.joining(", ")) + ')');
            }
            return Optional.empty();
        }
    }

    private static boolean isObjectVarArgs(MethodInvocationTree tree, VisitorState state) {
        Symbol.MethodSymbol methodSymbol = checkNotNull(ASTHelpers.getSymbol(tree), "symbol");
        if (!methodSymbol.isVarArgs()) {
            return false;
        }
        Symbol.VarSymbol varSymbol = Iterables.getLast(methodSymbol.getParameters());
        checkState(varSymbol.type instanceof Type.ArrayType, "Expected an array as last argument of a vararg method");
        Type.ArrayType arrayType = (Type.ArrayType) varSymbol.type;
        return ASTHelpers.isSameType(
                arrayType.getComponentType(),
                state.getTypeFromString(Object.class.getName()),
                state);
    }
}
