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
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Type;

@AutoService(BugChecker.class)
@BugPattern(
        name = "RawTypes",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        // Support SuppressWarnings("rawtypes"), which is already in use
        altNames = {"rawtypes"},
        summary = "Avoid raw types; add appropriate type parameters if possible.\n"
                + "This can be suppressed with @SuppressWarnings(\"rawtypes\") where necessary, such as when "
                + "interacting with older library code.")
public final class RawTypes extends BugChecker
        implements BugChecker.ClassTreeMatcher,
                BugChecker.MethodTreeMatcher,
                BugChecker.NewClassTreeMatcher,
                BugChecker.TypeCastTreeMatcher,
                BugChecker.VariableTreeMatcher {
    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        return testType(tree.getType());
    }

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        return testType(tree.getIdentifier());
    }

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        Description extendsResult = testType(tree.getExtendsClause());
        if (extendsResult != Description.NO_MATCH) {
            return extendsResult;
        }
        return testTypes(tree.getImplementsClause());
    }

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        return testType(tree.getReturnType());
    }

    @Override
    public Description matchTypeCast(TypeCastTree tree, VisitorState state) {
        return testType(tree.getType());
    }

    private Description testTypes(Iterable<? extends Tree> types) {
        for (Tree type : types) {
            Description description = testType(type);
            if (description != Description.NO_MATCH) {
                return description;
            }
        }
        return Description.NO_MATCH;
    }

    private Description testType(Tree type) {
        if (type == null) {
            return Description.NO_MATCH;
        }
        Type realType = ASTHelpers.getType(type);
        if (realType != null && realType.isRaw()) {
            return buildDescription(type)
                    .setMessage("Avoid raw types; add appropriate type parameters if possible. "
                            + "The type was: "
                            + MoreSuggestedFixes.prettyType(null, null, realType)
                            + "\nThis can be suppressed with @SuppressWarnings(\"rawtypes\") "
                            + "where necessary, such as when interacting with older library code.")
                    .build();
        }
        if (type.getKind() == Tree.Kind.PARAMETERIZED_TYPE) {
            return testTypes(((ParameterizedTypeTree) type).getTypeArguments());
        }
        return Description.NO_MATCH;
    }
}
