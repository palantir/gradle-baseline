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

import com.google.common.collect.Lists;
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.util.ErrorProneToken;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.lang.model.element.Name;

/** Additional utility functionality for {@link SuggestedFix} objects. */
final class MoreSuggestedFixes {

    /**
     * Renames a method invocation without modifying type arguments. This differs from
     * SuggestedFixes.renameMethodInvocation because it does not remove type arguments. Implementation is based on
     * error-prone SuggestedFixes.renameMethodInvocation (Apache 2)
     * https://github.com/google/error-prone/blob/master/check_api/src/main/java/com/google/errorprone/fixes/SuggestedFixes.java#L574
     */
    static SuggestedFix renameInvocationRetainingTypeArguments(
            MethodInvocationTree methodInvocationTree, String newMethodName, VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        Tree methodSelect = methodInvocationTree.getMethodSelect();
        int startPos;
        String extra = "";
        if (methodSelect instanceof MemberSelectTree) {
            MemberSelectTree memberSelectTree = (MemberSelectTree) methodSelect;
            startPos = state.getEndPosition(memberSelectTree.getExpression());
            extra = ".";
        } else if (methodSelect instanceof IdentifierTree) {
            JCTree methodInvocationJcTree = (JCTree) methodInvocationTree;
            startPos = methodInvocationJcTree.getStartPosition();
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

    /**
     * Replaces the name of the method being invoked in {@code tree} with {@code replacement}. This implementation is
     * forked from upstream error-prone to work around a bug that results in parameters being renamed in some scenarios:
     * https://github.com/google/error-prone/issues/1451.
     */
    public static SuggestedFix renameMethodInvocation(
            MethodInvocationTree tree, String replacement, VisitorState state) {
        Tree methodSelect = tree.getMethodSelect();
        Name identifier;
        int startPos;
        if (methodSelect instanceof MemberSelectTree) {
            identifier = ((MemberSelectTree) methodSelect).getIdentifier();
            startPos = state.getEndPosition(((MemberSelectTree) methodSelect).getExpression());
        } else if (methodSelect instanceof IdentifierTree) {
            identifier = ((IdentifierTree) methodSelect).getName();
            startPos = ((JCTree) tree).getStartPosition();
        } else {
            throw new IllegalStateException("Malformed tree:\n" + state.getSourceForNode(tree));
        }
        List<ErrorProneToken> tokens = state.getOffsetTokens(startPos, state.getEndPosition(tree));
        int depth = 0;
        for (ErrorProneToken token : Lists.reverse(tokens)) {
            if (depth == 0
                    && token.kind() == Tokens.TokenKind.IDENTIFIER
                    && token.name().equals(identifier)) {
                return SuggestedFix.replace(token.pos(), token.endPos(), replacement);
            } else if (token.kind() == Tokens.TokenKind.RPAREN) {
                depth++;
            } else if (token.kind() == Tokens.TokenKind.LPAREN) {
                depth--;
            }
        }
        throw new IllegalStateException("Malformed tree:\n" + state.getSourceForNode(tree));
    }

    /**
     * Identical to {@link SuggestedFixes#qualifyType(VisitorState, SuggestedFix.Builder, String)} unless the compiling
     * JVM is not supported by error-prone (JDK13) in which case a fallback is attempted.
     */
    static String qualifyType(VisitorState state, SuggestedFix.Builder fix, String typeName) {
        try {
            return SuggestedFixes.qualifyType(state, fix, typeName);
        } catch (LinkageError e) {
            // Work around https://github.com/google/error-prone/issues/1432
            // by avoiding the findIdent function. It's possible this may result
            // in colliding imports when classes have the same simple name, but
            // the output is correct in most cases, in the failures are easy for
            // humans to fix.
            for (int startOfClass = typeName.indexOf('.');
                    startOfClass > 0;
                    startOfClass = typeName.indexOf('.', startOfClass + 1)) {
                int endOfClass = typeName.indexOf('.', startOfClass + 1);
                if (endOfClass < 0) {
                    endOfClass = typeName.length();
                }
                if (!Character.isUpperCase(typeName.charAt(startOfClass + 1))) {
                    continue;
                }
                String className = typeName.substring(startOfClass + 1);
                fix.addImport(typeName.substring(0, endOfClass));
                return className;
            }
            return typeName;
        }
    }

    /**
     * Identical to {@link SuggestedFixes#prettyType(VisitorState, SuggestedFix.Builder, Type)} unless the compiling JVM
     * is not supported by error-prone (JDK13) in which case a fallback is attempted.
     */
    static String prettyType(@Nullable VisitorState state, @Nullable SuggestedFix.Builder fix, Type type) {
        try {
            return SuggestedFixes.prettyType(state, fix, type);
        } catch (LinkageError e) {
            // Work around https://github.com/google/error-prone/issues/1432
            // by using a path which cannot add imports, this does not throw on jdk13.
            return SuggestedFixes.prettyType(null, null, type);
        }
    }

    private MoreSuggestedFixes() {}
}
