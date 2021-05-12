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
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import javax.lang.model.element.Modifier;

/**
 * Checks that interface default methods in an immutables.org @Value.Immutable are
 * annotated with
 * <a href="https://immutables.github.io/immutable.html#default-attributes">@Value.Default</a> ,
 * <a href="https://immutables.github.io/immutable.html#derived-attributes">@Value.Derived</a> , or
 * <a href="https://immutables.github.io/immutable.html#lazy-attributes">@Value.Lazy</a> .
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "ImmutablesInterfaceDefaultValue",
        linkType = LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "@Value.Immutable interface default methods should be annotated with"
                + " @Value.Default, @Value.Derived, or @Value.Lazy")
public final class ImmutablesInterfaceDefaultValue extends BugChecker implements MethodTreeMatcher {

    private static final Matcher<Tree> MISSING_ANNOTATION_MATCHER = Matchers.allOf(
            Matchers.enclosingClass(Matchers.hasAnnotation("org.immutables.value.Value.Immutable")),
            Matchers.hasModifier(Modifier.DEFAULT),
            Matchers.not(Matchers.anyOf(
                    Matchers.hasAnnotation("org.immutables.value.Value.Default"),
                    Matchers.hasAnnotation("org.immutables.value.Value.Derived"),
                    Matchers.hasAnnotation("org.immutables.value.Value.Lazy"))));

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (MISSING_ANNOTATION_MATCHER.matches(tree, state)) {
            SuggestedFix.Builder builder = SuggestedFix.builder();
            String annotation = SuggestedFixes.qualifyType(state, builder, "org.immutables.value.Value.Default");
            return buildDescription(tree)
                    .addFix(builder.prefixWith(tree, "@" + annotation + " ").build())
                    .build();
        }
        return Description.NO_MATCH;
    }
}
