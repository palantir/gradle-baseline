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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DeprecatedGuavaObjects",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "The standard library java.util.Objects utilities replace Guava "
                + "com.google.common.base.Objects in java 1.7 and beyond.")
public final class DeprecatedGuavaObjects extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final String GUAVA_OBJECTS_NAME = "com.google.common.base.Objects";
    private static final String GUAVA_OBJECTS_EQUAL = GUAVA_OBJECTS_NAME + ".equal";
    private static final String GUAVA_OBJECTS_HASH_CODE = GUAVA_OBJECTS_NAME + ".hashCode";
    private static final String JAVA_OBJECTS_NAME = "java.util.Objects";
    private static final Matcher<ExpressionTree> EQUAL_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.google.common.base.Objects")
            .named("equal");
    private static final Matcher<ExpressionTree> HASH_CODE_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.google.common.base.Objects")
            .named("hashCode");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        boolean hashCode = HASH_CODE_MATCHER.matches(tree, state);
        boolean equal = EQUAL_MATCHER.matches(tree, state);
        if (hashCode || equal) {
            return buildDescription(tree)
                    .addFix(SuggestedFix.builder()
                            .removeImport(GUAVA_OBJECTS_NAME)
                            .removeStaticImport(GUAVA_OBJECTS_EQUAL)
                            .removeStaticImport(GUAVA_OBJECTS_HASH_CODE)
                            .addImport(JAVA_OBJECTS_NAME)
                            .replace(tree.getMethodSelect(), equal ? "Objects.equals" : "Objects.hash")
                            .build())
                    .build();
        }
        return Description.NO_MATCH;
    }
}
