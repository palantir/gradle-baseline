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

// modeled off of
// https://github.com/google/error-prone/blob/master/core/src/main/java/com/google/errorprone/bugpatterns/checkreturnvalue/UsingJsr305CheckReturnValue.java

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ImportTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ImportTree;
import com.sun.tools.javac.code.Type;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checker that recommends using Guava's version of {@code @VisibleForTesting} over other copies.
 */
@AutoService(BugChecker.class)
@BugPattern(summary = "Prefer Guava's @VisibleForTesting over other versions.", severity = SeverityLevel.WARNING)
public final class PreferGuavaVisibleForTesting extends BugChecker implements ImportTreeMatcher {
    private static final String GUAVA_VFT = "com.google.common.annotations.VisibleForTesting";
    private static final Set<String> OTHER_VFTS = Set.of(
            "org.apache.hadoop.thirdparty.com.google.common.annotations.VisibleForTesting",
            "org.jetbrains.annotations.VisibleForTesting",
            "com.facebook.react.common.annotations.VisibleForTesting",
            "org.apache.arrow.util.VisibleForTesting",
            "org.assertj.core.util.VisibleForTesting",
            "org.apache.flink.annotation.VisibleForTesting",
            "com.cronutils.utils.VisibleForTesting",
            "com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting",
            "org.sparkproject.guava.annotations.VisibleForTesting",
            "org.apache.hadoop.classification.VisibleForTesting",
            "org.jheaps.annotations.VisibleForTesting",
            "io.debezium.annotation.VisibleForTesting",
            "graphql.VisibleForTesting",
            "avro.shaded.com.google.common.annotations.VisibleForTesting");

    private static final Supplier<Set<Type>> OTHER_TYPES = VisitorState.memoize(
            state -> OTHER_VFTS.stream().map(state::getTypeFromString).collect(Collectors.toSet()));

    @Override
    public Description matchImport(ImportTree tree, VisitorState state) {
        for (Type otherType : OTHER_TYPES.get(state)) {
            if (ASTHelpers.isSameType(ASTHelpers.getType(tree.getQualifiedIdentifier()), otherType, state)) {
                SuggestedFix fix = SuggestedFix.builder()
                        .removeImport(otherType.toString())
                        .addImport(GUAVA_VFT)
                        .build();
                return describeMatch(tree, fix);
            }
        }
        return Description.NO_MATCH;
    }
}
