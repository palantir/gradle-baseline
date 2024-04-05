/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;

/**
 * Warns that users should not have a {@link java.util.regex.Pattern} as a key to a Set or Map.
 */
@AutoService(BugChecker.class)
@BugPattern(
        summary = "Record type has an array field and hasn't overridden equals/hashcode. By default array equality"
                + " performs reference equality only. Consider using an immutable Collection for the field, using"
                + " Immutables instead of the record, or overriding equals/hashCode in the record.",
        severity = SeverityLevel.WARNING)
public final class DangerousRecordArrayField extends SuppressibleBugChecker implements BugChecker.ClassTreeMatcher {

    private static final Matcher<VariableTree> IS_ARRAY_VARIABLE = Matchers.isArrayType();
    private static final Matcher<MethodTree> EQUALS_MATCHER = Matchers.equalsMethodDeclaration();
    private static final Matcher<MethodTree> HASHCODE_MATCHER = Matchers.hashCodeMethodDeclaration();

    @Override
    public Description matchClass(ClassTree classTree, VisitorState state) {
        ClassSymbol classSymbol = ASTHelpers.getSymbol(classTree);
        if (!ASTHelpers.isRecord(classSymbol)) {
            return Description.NO_MATCH;
        }
        if (!hasArrayField(classTree, state)) {
            return Description.NO_MATCH;
        }
        if (hasNonTrivialEqualsAndHashCode(classTree, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(classTree).build();
    }

    private static boolean hasArrayField(ClassTree classTree, VisitorState state) {
        for (Tree member : classTree.getMembers()) {
            if (member instanceof VariableTree) {
                VariableTree variableTree = (VariableTree) member;

                if (IS_ARRAY_VARIABLE.matches(variableTree, state)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasNonTrivialEqualsAndHashCode(ClassTree classTree, VisitorState state) {
        boolean hasEquals = false;
        boolean hasHashCode = false;
        for (Tree member : classTree.getMembers()) {
            if (member instanceof MethodTree) {
                MethodTree methodTree = (MethodTree) member;

                // We want to check if the equals & hashCode methods have actually been overridden (i.e. don't just
                // call Object.equals)
                hasEquals = hasEquals || EQUALS_MATCHER.matches(methodTree, state);
                hasHashCode = hasHashCode || HASHCODE_MATCHER.matches(methodTree, state);
            }
        }

        return hasEquals && hasHashCode;
    }
}
