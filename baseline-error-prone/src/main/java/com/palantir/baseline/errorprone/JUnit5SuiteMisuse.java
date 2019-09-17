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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.AnnotationMatcherUtils;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AutoService(BugChecker.class)
@BugPattern(
        name = "JUnit5SuiteMisuse",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Referencing JUnit5 tests from JUnit4 Suites will silently not work")
public final class JUnit5SuiteMisuse extends BugChecker
        implements BugChecker.ClassTreeMatcher, BugChecker.AnnotationTreeMatcher {

    private static final long serialVersionUID = 1L;

    // We remember classes and validate them later because error-prone doesn't let us arbitrarily explore classes we
    // discover when reading the @SuiteClasses annotation.
    private static final Set<Type.ClassType> knownJUnit5TestClasses = new HashSet<>();
    private static final Set<Type.ClassType> referencedBySuites = new HashSet<>();

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (!JUnit5RuleUsage.hasJunit5TestCases.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Type.ClassType type = ASTHelpers.getType(tree);
        knownJUnit5TestClasses.add(type); // accumulate these so we can check them when visiting suiteclasses

        if (referencedBySuites.contains(type)) {
            return buildDescription(tree)
                    .setMessage("Class uses JUnit5 tests but is referenced by a JUnit4 SuiteClasses")
                    .build();
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchAnnotation(AnnotationTree tree, VisitorState state) {
        if (!Matchers.isSameType("org.junit.runners.Suite.SuiteClasses").matches(tree, state)) {
            return Description.NO_MATCH;
        }

        for (JCTree.JCFieldAccess field : getReferencedClasses(tree, state)) {
            Type.ClassType classType = (Type.ClassType) field.selected.type;
            referencedBySuites.add(classType);

            if (knownJUnit5TestClasses.contains(classType)) {
                return buildDescription(tree)
                        .setMessage("Don't reference JUnit5 test classes from JUnit4 SuiteClasses annotation")
                        .build();
            }
        }

        return Description.NO_MATCH;
    }

    private static List<JCTree.JCFieldAccess> getReferencedClasses(AnnotationTree tree, VisitorState state) {
        ExpressionTree value = AnnotationMatcherUtils.getArgument(tree, "value");

        if (value == null) {
            return Collections.emptyList();
        }

        if (value instanceof JCTree.JCFieldAccess) {
            return Collections.singletonList((JCTree.JCFieldAccess) value);
        }

        if (value instanceof JCTree.JCNewArray) {
            ImmutableList.Builder<JCTree.JCFieldAccess> list = ImmutableList.builder();
            for (JCTree.JCExpression elem : ((JCTree.JCNewArray) value).elems) {
                list.add((JCTree.JCFieldAccess) elem);
            }
            return list.build();
        }

        throw new UnsupportedOperationException(String.format(
                "Unable to get referenced classes for %s of type %s",
                state.getSourceForNode(tree),
                value.getClass()));
    }
}
