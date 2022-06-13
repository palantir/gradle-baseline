/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ClassTree;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Disallow non-reusable atlasdb persisters.")
public final class NonReusableAtlasdbPersister extends BugChecker implements BugChecker.ClassTreeMatcher {
    private static final String ERROR_MESSAGE = "All AtlasDB persisters must be re-usable. Non re-usable persisters "
            + "are allocated for every read/write, "
            + "causing significantly more object allocations. This is particularly bad when object mappers are "
            + "created in the persister's constructor as the class cache cannot be re-used. Do check the persister is "
            + "currently reusable. If you have questions here, feel free to ask around internally, or "
            + "read the source.";

    private static final String ATLASDB_PERSISTER = "com.palantir.atlasdb.persist.api.Persister";
    private static final Matcher<ClassTree> persisterClass = MoreMatchers.isSubtypeOf(ATLASDB_PERSISTER);

    private static final String REUSABLE_ANNOTATION = "com.palantir.atlasdb.annotation.Reusable";
    private static final Matcher<ClassTree> reusableAnnotationMatcher = Matchers.hasAnnotation(REUSABLE_ANNOTATION);

    private static final long serialVersionUID = 1L;

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (!persisterClass.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        if (reusableAnnotationMatcher.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
        String annotation = SuggestedFixes.qualifyType(state, fixBuilder, REUSABLE_ANNOTATION);
        fixBuilder.prefixWith(tree, "@" + annotation + "\n");
        SuggestedFix addAnnotation = fixBuilder.build();

        return buildDescription(tree)
                .addFix(addAnnotation)
                .setMessage(ERROR_MESSAGE)
                .build();
    }
}
