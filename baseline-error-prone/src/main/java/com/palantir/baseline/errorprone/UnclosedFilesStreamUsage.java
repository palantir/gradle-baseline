/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern.Category;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreePath;
import java.util.Optional;
import java.util.regex.Pattern;

@AutoService(BugChecker.class)
@BugPattern(
        name = "UnclosedFilesStreamUsage",
        category = Category.ONE_OFF,
        severity = SeverityLevel.ERROR,
        summary = "Ensure a stream returned by java.nio.file.Files#{find,lines,list,walk} "
                + "is closed to prevent leaking file descriptors.")
public final class UnclosedFilesStreamUsage extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final MethodMatchers.MethodNameMatcher filesStreamMatcher = MethodMatchers.staticMethod()
            .onClass("java.nio.file.Files")
            .withNameMatching(Pattern.compile("find|lines|list|walk"));

    private static final Matcher<Tree> tryResourcesMatcher = (Matcher<Tree>)
            (methodInvocationTree, state) -> Optional.ofNullable(state.findEnclosing(TryTree.class))
                    .map(TryTree::getResources)
                    .map(resourcesTrees -> resourcesTrees.stream()
                            .map(resourcesTree -> findPathToEnclosingTree(state.getPath(), resourcesTree))
                            .anyMatch(Optional::isPresent))
                    .orElse(false);

    private static Optional<TreePath> findPathToEnclosingTree(TreePath initialPath, Tree tree) {
        TreePath enclosingPath = initialPath;
        while (enclosingPath != null) {
            if (enclosingPath.getLeaf().equals(tree)) {
                return Optional.of(enclosingPath);
            }
            enclosingPath = enclosingPath.getParentPath();
        }
        return Optional.empty();
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!Matchers.allOf(filesStreamMatcher, Matchers.not(tryResourcesMatcher)).matches(tree, state)) {
            return Description.NO_MATCH;
        }
        return buildDescription(tree)
                .setMessage("java.nio.file.Files must be called within a try-with-resources block")
                .build();
    }

}
