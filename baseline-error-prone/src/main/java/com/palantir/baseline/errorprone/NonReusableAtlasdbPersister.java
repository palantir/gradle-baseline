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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static final String LEGACY_ATLASDB_PERSISTER = "com.palantir.atlasdb.persist.api.Persister";
    private static final Matcher<ClassTree> implementsLegacyPersister =
            Matchers.isDirectImplementationOf(LEGACY_ATLASDB_PERSISTER);
    private static final Matcher<Tree> legacyPersister = Matchers.isSameType(LEGACY_ATLASDB_PERSISTER);

    private static final String REUSABLE_ANNOTATION = "com.palantir.atlasdb.annotation.Reusable";
    private static final Matcher<ClassTree> hasReusableAnnotationMatcher = Matchers.hasAnnotation(REUSABLE_ANNOTATION);
    private static final Matcher<AnnotationTree> reusableAnnotationMatcher = Matchers.isType(REUSABLE_ANNOTATION);

    private static final String ATLASDB_REUSABLE_PERSISTER = "com.palantir.atlasdb.persist.api.ReusablePersister";

    private static final long serialVersionUID = 1L;

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (!implementsLegacyPersister.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<? extends Tree> legacyImplementClauseOpt = tree.getImplementsClause().stream()
                .filter(clause -> legacyPersister.matches(clause, state))
                .findAny();

        if (legacyImplementClauseOpt.isEmpty()) {
            return Description.NO_MATCH;
        }

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
        String reusablePersister = SuggestedFixes.qualifyType(state, fixBuilder, ATLASDB_REUSABLE_PERSISTER);

        Tree legacyImplementCause = legacyImplementClauseOpt.get();

        // Extract the generic type
        String typeSuffix = Optional.ofNullable(
                        ASTHelpers.getType(legacyImplementCause).getTypeArguments())
                .map(typeArgs -> typeArgs.stream()
                        .map(typeArg -> SuggestedFixes.qualifyType(state, fixBuilder, typeArg.toString()))
                        .collect(Collectors.joining(", ", "<", ">")))
                .orElse("");
        fixBuilder.replace(legacyImplementCause, reusablePersister + typeSuffix);

        // There isn't a valid use of this import anymore, clean it up!
        fixBuilder.removeImport(LEGACY_ATLASDB_PERSISTER);

        // Remove the now defunct reusable annotations
        if (hasReusableAnnotationMatcher.matches(tree, state)) {
            ASTHelpers.getAnnotations(tree).stream()
                    .filter(annotation -> reusableAnnotationMatcher.matches(annotation, state))
                    .forEach(reusableAnnotation -> fixBuilder.replace(reusableAnnotation, ""));
            // Same here, there isn't a valid use of this import anymore, clean it up!
            fixBuilder.removeImport(REUSABLE_ANNOTATION);
        }

        return buildDescription(tree)
                .addFix(fixBuilder.build())
                .setMessage(ERROR_MESSAGE)
                .build();
    }
}
