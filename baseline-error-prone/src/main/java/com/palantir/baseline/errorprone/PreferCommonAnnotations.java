/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ImportTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ImportTree;
import com.sun.tools.javac.code.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Checker that recommends using the common version of an annotation.
 *
 * Examples:
 * - Guava's version of {@code @VisibleForTesting} over other copies.
 */
@AutoService(BugChecker.class)
@BugPattern(
        summary = "Prefer the common version of annotations over other copies.",
        severity = SeverityLevel.SUGGESTION)
public final class PreferCommonAnnotations extends BugChecker implements ImportTreeMatcher {

    /** ClassName -> preferred import. */
    private static final Map<String, String> PREFERRED_IMPORTS =
            Map.of("VisibleForTesting", "com.google.common.annotations.VisibleForTesting");

    @Override
    public Description matchImport(ImportTree tree, VisitorState state) {
        Type importType = ASTHelpers.getType(tree.getQualifiedIdentifier());
        if (importType == null) {
            return Description.NO_MATCH;
        }
        String importName = importType.toString();
        for (Map.Entry<String, String> entry : PREFERRED_IMPORTS.entrySet()) {
            String affectedClassName = entry.getKey();
            String preferredType = entry.getValue();
            if (importName.endsWith(affectedClassName) && !Objects.equals(importName, preferredType)) {
                SuggestedFix fix = SuggestedFix.builder()
                        .removeImport(importName)
                        .addImport(preferredType)
                        .build();
                return describeMatch(tree, fix);
            }
        }
        return Description.NO_MATCH;
    }
}
