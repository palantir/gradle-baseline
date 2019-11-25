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

import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFix;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import java.util.stream.Collectors;

/**
 * Additional utility functionality for {@link SuggestedFix} objects.
 */
final class MoreSuggestedFixes {

    /**
     * Renames a method invocation without modifying type arguments.
     * This differs from SuggestedFixes.renameMethodInvocation because it does not
     * remove type arguments.
     */
    static SuggestedFix renameInvocationRetainingTypeArguments(
            MethodInvocationTree methodInvocationTree,
            String newMethodName,
            VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        Tree methodSelect = methodInvocationTree.getMethodSelect();
        int startPos;
        String extra = "";
        if (methodSelect instanceof MemberSelectTree) {
            startPos = state.getEndPosition(((MemberSelectTree) methodSelect).getExpression());
            extra = ".";
        } else if (methodSelect instanceof IdentifierTree) {
            startPos = ((JCTree) methodInvocationTree).getStartPosition();
        } else {
            return fix.build();
        }
        if (!methodInvocationTree.getTypeArguments().isEmpty()) {
            extra += methodInvocationTree.getTypeArguments().stream()
                    .map(state::getSourceForNode)
                    .collect(Collectors.joining(", ", "<", ">"));
        }
        int endPos = state.getEndPosition(methodSelect);
        return fix.replace(startPos, endPos, extra + newMethodName).build();
    }

    private MoreSuggestedFixes() {}
}
