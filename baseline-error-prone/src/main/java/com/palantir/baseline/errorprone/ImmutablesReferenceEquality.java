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
package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.AbstractReferenceEquality;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;

@AutoService(BugChecker.class)
@BugPattern(
        linkType = LinkType.CUSTOM,
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = SeverityLevel.ERROR,
        summary = "Comparison of Immutables value using reference equality instead of value equality.")
public final class ImmutablesReferenceEquality extends AbstractReferenceEquality {

    @Override
    protected boolean matchArgument(ExpressionTree tree, VisitorState state) {
        Type type = ASTHelpers.getType(tree);
        if (!(type.tsym instanceof ClassSymbol)) {
            return false;
        }

        ClassSymbol symbol = (ClassSymbol) type.tsym;

        return ASTHelpers.hasAnnotation(symbol, "org.immutables.value.Value.Immutable", state);
    }
}
