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
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Development Practices: Writing good unit tests.
 */
@AutoService(BugChecker.class)
@BugPattern(
        name = "DuplicateArgumentTypes",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Duplicate argument types")
public final class DuplicateArgumentTypes extends BugChecker implements BugChecker.MethodTreeMatcher {

    // TODO (jshah) - sort out how to deal with multiple suppliers, even if the type params are not
    //  subtypes of each other (maybe do type.getTypeParameters() and recurse?)
    // how to check that arbitrary Tree type2 is a subtype of a fixed Class...
    // or can just do Matchers.isSubtypeOf(Integer.class)... lol oops
    // actually cannot when trying the other way around
    // ASTHelpers.isSubtype(Suppliers.typeFromClass(Integer.class).get(state), ASTHelpers.getType(type2), state)

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        AtomicBoolean bad = new AtomicBoolean(false);
        tree.getParameters().forEach(param -> {
            Tree type = param.getType();
            tree.getParameters().forEach(param2 -> {
                if (!param.equals(param2)) {
                    Tree type2 = param2.getType();
                    bad.set(bad.get() || isSubType(getType(type, state), getType(type2, state), state));
                }
            });
        });

        if (bad.get()) {
            return buildDescription(tree).setMessage("Consider using a builder instead").build();
        }
        return Description.NO_MATCH;
    }

    private boolean isSubtypeOf(Tree type, Tree possibleSubType, VisitorState state) {
        return getMatcher(type, state).matches(possibleSubType, state);
    }

    private boolean isPrimitive(Tree type, VisitorState state) {
        return Matchers.isPrimitiveType().matches(type, state);
    }

    private Type getType(Tree type, VisitorState state) {
        if (isPrimitive(type, state)) {
            switch (type.toString()) {
                case "int":
                    return extractType(Integer.class, state);
                case "byte":
                    return extractType(Byte.class, state);
                case "char":
                    return extractType(Character.class, state);
                case "double":
                    return extractType(Double.class, state);
                case "long":
                    return extractType(Long.class, state);
                case "short":
                    return extractType(Short.class, state);
                case "float":
                    return extractType(Float.class, state);
                case "boolean":
                    return extractType(Boolean.class, state);
                default:
                    break;
            }
        }
        return ASTHelpers.getType(type);
    }

    private Type extractType(Class clazz, VisitorState state) {
        return Suppliers.typeFromClass(clazz).get(state);
    }

    private boolean isSubType(Type t1, Type t2, VisitorState state) {
        return ASTHelpers.isSubtype(t1, t2, state);
    }

    private Matcher<Tree> getMatcher(Tree type, VisitorState state) {
        if (isPrimitive(type, state)) {
            // System.out.println(type.toString());
            switch (type.toString()) {
                case "int":
                    return Matchers.isSubtypeOf(Integer.class);
                case "byte":
                    return Matchers.isSubtypeOf(Byte.class);
                case "char":
                    return Matchers.isSubtypeOf(Character.class);
                case "double":
                    return Matchers.isSubtypeOf(Double.class);
                case "long":
                    return Matchers.isSubtypeOf(Long.class);
                case "short":
                    return Matchers.isSubtypeOf(Short.class);
                case "float":
                    return Matchers.isSubtypeOf(Float.class);
                case "boolean":
                    return Matchers.isSubtypeOf(Boolean.class);
                default:
                    break;
            }
        }
        return Matchers.isSubtypeOf($ -> ASTHelpers.getType(type));
    }
}
