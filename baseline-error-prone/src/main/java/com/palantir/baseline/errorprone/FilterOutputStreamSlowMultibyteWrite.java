/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

// Portions adapted from
// https://github.com/google/error-prone/blob/e4769fd/core/src/main/java/com/google/errorprone/bugpatterns/InputStreamSlowMultibyteRead.java
// Copyright 2016 The Error Prone Authors.

package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.BugPattern.StandardTags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.Name;
import java.io.FilterOutputStream;
import javax.lang.model.element.ElementKind;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        summary = "Please also override `void write(byte[], int, int)`, "
                + "otherwise multi-byte writes to this output stream are likely to be slow.",
        severity = SeverityLevel.WARNING,
        tags = StandardTags.PERFORMANCE)
public final class FilterOutputStreamSlowMultibyteWrite extends BugChecker implements ClassTreeMatcher {

    private static final Matcher<ClassTree> IS_FILTER_OUTPUT_STREAM = Matchers.isSubtypeOf(FilterOutputStream.class);

    private static final Matcher<MethodTree> WRITE_INT_METHOD = Matchers.allOf(
            Matchers.methodIsNamed("write"),
            Matchers.methodReturns(Suppliers.VOID_TYPE),
            Matchers.methodHasParameters(Matchers.isSameType(Suppliers.INT_TYPE)));

    private static final Supplier<Name> WRITE = VisitorState.memoize(state -> state.getName("write"));
    private static final Supplier<Type> FILTER_OUTPUT_STREAM =
            VisitorState.memoize(state -> state.getTypeFromString(FilterOutputStream.class.getTypeName()));

    @Override
    public Description matchClass(ClassTree classTree, VisitorState state) {
        if (!IS_FILTER_OUTPUT_STREAM.matches(classTree, state)) {
            return Description.NO_MATCH;
        }

        TypeSymbol thisClassSymbol = ASTHelpers.getSymbol(classTree);
        if (thisClassSymbol.getKind() != ElementKind.CLASS) {
            return Description.NO_MATCH;
        }

        Type intType = state.getSymtab().intType;
        MethodSymbol singleByteWriteMethod = ASTHelpers.resolveExistingMethod(
                state, thisClassSymbol, WRITE.get(state), ImmutableList.of(intType), ImmutableList.of());
        if (singleByteWriteMethod == null) {
            return Description.NO_MATCH;
        }

        Type filterOutputStreamType = FILTER_OUTPUT_STREAM.get(state);
        if (filterOutputStreamType == null) {
            return Description.NO_MATCH;
        }

        Type byteArrayType = state.arrayTypeForType(state.getSymtab().byteType);
        MethodSymbol multiByteWriteMethod = ASTHelpers.resolveExistingMethod(
                state,
                thisClassSymbol,
                WRITE.get(state),
                ImmutableList.of(byteArrayType, intType, intType),
                ImmutableList.of());

        if ((multiByteWriteMethod != null)
                && (multiByteWriteMethod.owner.equals(thisClassSymbol)
                        || (singleByteWriteMethod.owner.equals(multiByteWriteMethod.owner)
                                && !singleByteWriteMethod.owner.equals(filterOutputStreamType.tsym)))) {
            // non-FilterOutputStream class defines both single & multibyte write
            return Description.NO_MATCH;
        }

        // Find method that overrides the single-byte write. It should also override the multibyte write.
        MethodTree writeByteMethod = classTree.getMembers().stream()
                .filter(MethodTree.class::isInstance)
                .map(MethodTree.class::cast)
                .filter(m -> WRITE_INT_METHOD.matches(m, state))
                .findFirst()
                .orElse(null);

        return writeByteMethod == null ? describeMatch(classTree) : describeMatch(writeByteMethod);
    }
}
