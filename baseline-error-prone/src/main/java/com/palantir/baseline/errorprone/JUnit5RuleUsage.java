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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(
        name = "JUnit5RuleUsage",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Using Rule/ClassRules in Junit5 tests results in the rules silently not executing")
public final class JUnit5RuleUsage extends BugChecker implements BugChecker.ClassTreeMatcher {
    private static final String JUNIT4_RULE = "org.junit.Rule";
    private static final String JUNIT4_CLASS_RULE = "org.junit.ClassRule";
    private static final String JUNIT5_TEST_ANNOTATION = "org.junit.jupiter.api.Test";
    private static final String RULE_MIGRATION_SUPPORT =
            "org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport";

    private static final Matcher<ClassTree> hasMigrationSupport = Matchers.hasAnnotation(RULE_MIGRATION_SUPPORT);
    private static final Matcher<ClassTree> hasJunit5TestCases =
            Matchers.hasMethod(Matchers.hasAnnotationOnAnyOverriddenMethod(JUNIT5_TEST_ANNOTATION));
    private static final Matcher<ClassTree> hasJunit4Rules = hasVariable(
            Matchers.anyOf(hasAnnotationOnVariable(JUNIT4_CLASS_RULE), hasAnnotationOnVariable(JUNIT4_RULE)));

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (!hasMigrationSupport.matches(tree, state)
                && hasJunit5TestCases.matches(tree, state)
                && hasJunit4Rules.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Do not use Rule/ClassRule with junit-jupiter")
                    .build();
        }

        return Description.NO_MATCH;
    }

    static Matcher<ClassTree> hasVariable(Matcher<VariableTree> matcher) {
        return (classTree, state) -> classTree.getMembers()
                .stream()
                .filter(tree -> tree instanceof VariableTree)
                .anyMatch(tree -> matcher.matches((VariableTree) tree, state));
    }

    static Matcher<VariableTree> hasAnnotationOnVariable(String annotation) {
        return (variableTree, state) -> {
            Symbol.VarSymbol sym = ASTHelpers.getSymbol(variableTree);
            return sym != null && ASTHelpers.hasAnnotation(sym, annotation, state);
        };
    }
}
