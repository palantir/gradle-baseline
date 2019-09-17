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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AutoService(BugChecker.class)
@BugPattern(
        name = "JUnit5SuiteMisuse",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Mixing JUnit5 tests with JUnit4 Suites will silently not work")
public final class JUnit5SuiteMisuse extends BugChecker implements BugChecker.ClassTreeMatcher, BugChecker.AnnotationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Set<Type.ClassType> knownJUnit5TestClasses = new HashSet<>();
    private static final Set<Type.ClassType> referencedBySuites = new HashSet<>();

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (!JUnit5RuleUsage.hasJunit5TestCases.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Type.ClassType type = ASTHelpers.getType(tree);
        knownJUnit5TestClasses.add(type); // we accumulate these so that visiting suiteclasses can be useful

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

        for (JCTree.JCFieldAccess field : getReferencedClasses(tree)) {
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

    private static List<JCTree.JCFieldAccess> getReferencedClasses(AnnotationTree tree) {
        final ExpressionTree value = AnnotationMatcherUtils.getArgument(tree, "value");

        if (value instanceof JCTree.JCFieldAccess) {
            return Collections.singletonList((JCTree.JCFieldAccess) value);
        }

        if (value instanceof JCTree.JCNewArray) {
            List<JCTree.JCFieldAccess> fields = new ArrayList<>();
            for (JCTree.JCExpression elem : ((JCTree.JCNewArray) value).elems) {
                fields.add((JCTree.JCFieldAccess) elem);
            }
            return fields;
        }

        throw new UnsupportedOperationException(
                "Unable to get referenced classes for " + tree.toString() + " of type " + value.getClass());
    }
}
