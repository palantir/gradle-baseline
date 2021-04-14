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
import com.google.errorprone.bugpatterns.inject.ElementPredicates;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import org.immutables.value.Value;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ImmutablesStyle",
        linkType = LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = SeverityLevel.WARNING,
        summary = "Using an inline Immutables @Value.Style annotation or meta-annotation with non-SOURCE "
                + "rentention forces consumers to add a Immutables annotations to their compile classpath."
                + "Instead use a meta-annotation with SOURCE retention."
                + "See https://github.com/immutables/immutables/issues/291.")
public final class ImmutablesStyle extends BugChecker implements BugChecker.ClassTreeMatcher {

    private static final Matcher<ClassTree> INLINE_STYLE_ANNOTATION = Matchers.hasAnnotation(Value.Style.class);

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        switch (tree.getKind()) {
            case CLASS:
            case INTERFACE:
                if (INLINE_STYLE_ANNOTATION.matches(tree, state)) {
                    return describeMatch(tree);
                }
                break;
            case ANNOTATION_TYPE:
                ClassSymbol classSymbol = ASTHelpers.getSymbol(tree);
                if (!ElementPredicates.hasSourceRetention(classSymbol)) {
                    return describeMatch(tree);
                }
                break;
            default:
                break;
        }

        return Description.NO_MATCH;
    }
}
