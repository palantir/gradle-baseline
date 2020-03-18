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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    // @Override
    public Description matchMethoda(MethodTree tree, VisitorState state) {
        Set<String> types = new HashSet<>();
        AtomicInteger counter = new AtomicInteger(0);
        tree.getParameters().forEach(la -> {
            types.add(la.getType().toString());
            counter.incrementAndGet();
        });
        if (counter.get() > types.size()) {
            return buildDescription(tree).build();
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        AtomicBoolean bad = new AtomicBoolean(false);
        tree.getParameters().forEach(param -> {
            Tree type = param.getType();
            tree.getParameters().forEach(param2 -> {
                if (!param.equals(param2)) {
                    Tree type2 = param2.getType();
                    // Matcher<Tree> m = Matchers.isSubtypeOf($ -> ASTHelpers.getType(type));
                    bad.set(bad.get() || (isSubtypeOf(type, type2, state) || isSubtypeOf(type2,
                            type, state)));
                }
            });
        });

        System.out.println(bad.get());
        return Description.NO_MATCH;
    }

    private boolean isSubtypeOf(Tree clazz, Tree tree, VisitorState state) {
        boolean x = Matchers.isSubtypeOf($ -> ASTHelpers.getType(clazz)).matches(tree, state);
        if(x) {
            System.out.println("madness abides");
        }
        return x;
    }
}
