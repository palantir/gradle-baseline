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
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import java.util.concurrent.atomic.AtomicBoolean;
import org.immutables.value.Value;

/**
 * Development Practices: Writing good unit tests.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "DuplicateArgumentTypes",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Some argument types are equal or are subtypes of each other. Consider using a builder instead.")
public final class DuplicateArgumentTypes extends BugChecker implements BugChecker.MethodTreeMatcher {

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        AtomicBoolean badMethod = new AtomicBoolean(false);

        tree.getParameters().forEach(param1 -> tree.getParameters().forEach(param2 -> {
            if (!param1.equals(param2)) {
                Type outerType = getType(param1.getType(), state);
                Type innerType = getType(param2.getType(), state);
                TypesToCompare typesToCompare = ImmutableTypesToCompare.builder()
                        .subType(outerType)
                        .superType(innerType)
                        .build();
                badMethod.set(badMethod.get() || subTypeCheck(typesToCompare, state));
            }
        }));

        if (badMethod.get()) {
            return buildDescription(tree).build();
        }

        return Description.NO_MATCH;
    }

    private boolean subTypeCheck(TypesToCompare typesToCompare, VisitorState state) {
        if (!isSubType(typesToCompare, state)) {
            return false;
        } else {
            return Streams.zip(
                            typesToCompare.subType().allparams().stream(),
                            typesToCompare.superType().allparams().stream(),
                            (left, right) -> (subTypeCheck(
                                            ImmutableTypesToCompare.builder()
                                                    .subType(left)
                                                    .superType(right)
                                                    .build(),
                                            state)
                                    || subTypeCheck(
                                            ImmutableTypesToCompare.builder()
                                                    .subType(right)
                                                    .superType(left)
                                                    .build(),
                                            state)))
                    .allMatch(y -> y);
        }
    }

    private boolean isSubType(TypesToCompare typesToCompare, VisitorState state) {
        return ASTHelpers.isSubtype(typesToCompare.subType(), typesToCompare.superType(), state);
    }

    private Type getType(Tree type, VisitorState state) {
        if (isPrimitive(type, state)) {
            switch (state.getSourceForNode(type)) {
                case "int":
                    return Suppliers.typeFromClass(Integer.class).get(state);
                case "byte":
                    return Suppliers.typeFromClass(Byte.class).get(state);
                case "char":
                    return Suppliers.typeFromClass(Character.class).get(state);
                case "double":
                    return Suppliers.typeFromClass(Double.class).get(state);
                case "long":
                    return Suppliers.typeFromClass(Long.class).get(state);
                case "short":
                    return Suppliers.typeFromClass(Short.class).get(state);
                case "float":
                    return Suppliers.typeFromClass(Float.class).get(state);
                case "boolean":
                    return Suppliers.typeFromClass(Boolean.class).get(state);
            }
        }
        return ASTHelpers.getType(type);
    }

    private boolean isPrimitive(Tree type, VisitorState state) {
        return Matchers.isPrimitiveType().matches(type, state);
    }

    @Value.Immutable
    @Value.Style(stagedBuilder = true)
    interface TypesToCompare {
        Type subType();

        Type superType();
    }
}
