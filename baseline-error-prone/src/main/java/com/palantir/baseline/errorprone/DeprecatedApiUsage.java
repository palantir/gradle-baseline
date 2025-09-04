/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.Tree;
import java.util.Optional;

/**
 * This check is meant to replace usage of the `-Werror` and `-Xlint:deprecation` compiler flags, which cannot be
 *   automatically suppressed by suppressible-error-prone and therefore block library upgrades as soon as a deprecation
 *   is introduced that is being relied upon.
 * Instead, this check is meant to be auto-suppressed upon upgrades and prevent backsliding by unintentionally relying
 *   upon deprecated APIs.
 *
 * Note that it explicitly does not handle APIs that are deprecated for removal, as those should be handled by
 *   {@link DeprecatedForRemovalApiUsage}.
 */
@SuppressWarnings("BugPatternNaming")
@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Deprecated APIs should not be relied upon as they may be removed in a future release.",
        // Use deprecation as the main name for the check, for familiarity with the javac flag.
        name = "deprecation",
        altNames = "DeprecatedApiUsage")
public final class DeprecatedApiUsage extends AbstractDeprecatedApiCheck {

    private static final String MESSAGE_DETAILS =
            " - this may be removed in a future release and prevent library upgrades. Note: This error comes from "
                    + "the DeprecatedApiUsage error-prone check, replacing the java compiler flag '-Xlint:deprecation.'"
                    + " Use @SuppressWarnings(\"deprecation\") to suppress this error.";

    private static final Matcher<Tree> DEPRECATED_SYMBOL =
            Matchers.symbolMatcher((symbol, state) -> symbol.isDeprecated() && !symbol.isDeprecatedForRemoval());

    @Override
    protected boolean isDeprecationWarning(Tree tree, VisitorState state) {
        return DEPRECATED_SYMBOL.matches(tree, state);
    }

    @Override
    protected String getErrorDescription(Optional<String> qualifiedName) {
        return qualifiedName
                        .map(name -> String.format("%s is deprecated", name))
                        .orElse("Deprecated API usage")
                + MESSAGE_DETAILS;
    }
}
