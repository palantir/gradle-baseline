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
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ClassTree;
import org.immutables.value.Value;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ImmutablesStyleCollision",
        linkType = LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Immutables @Value.Style inline annotation should not be present alongside a Style "
                + "meta-annotation, as there is no Style merging. You should either modify the "
                + "meta-annotation, or add the meta-annotation's fields to your inline @Value.Style "
                + "declaration.")
public final class ImmutablesStyleCollision extends BugChecker implements BugChecker.ClassTreeMatcher {
    private static final Matcher<ClassTree> INLINE_STYLE_ANNOTATION = Matchers.hasAnnotation(Value.Style.class);
    private static final Matcher<ClassTree> STYLE_META_ANNOTATION =
            Matchers.annotations(ChildMultiMatcher.MatchType.AT_LEAST_ONE, Matchers.hasAnnotation(Value.Style.class));
    private static final Matcher<ClassTree> MATCHER = Matchers.allOf(INLINE_STYLE_ANNOTATION, STYLE_META_ANNOTATION);

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (MATCHER.matches(tree, state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }
}
