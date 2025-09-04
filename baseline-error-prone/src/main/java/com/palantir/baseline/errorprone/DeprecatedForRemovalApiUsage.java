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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.Optional;

/**
 * This check is meant to replace usage of the `-Werror` and `-Xlint:removal` compiler flags (the latter being default),
 *   which cannot be automatically suppressed by suppressible-error-prone and therefore block library upgrades as soon
 *   as a deprecation-for-removal is introduced that is being relied upon.
 * Instead, this check is meant to be auto-suppressed upon upgrades and prevent backsliding by unintentionally relying
 *   upon deprecated APIs.
 *
 * Note that it explicitly does not handle APIs that are deprecated, but not for removal, as those should be handled by
 *   {@link DeprecatedApiUsage}.
 */
@SuppressWarnings("BugPatternNaming")
@AutoService(BugChecker.class)
@BugPattern(
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Deprecated-for-removal APIs should not be relied upon as they will be removed in a future release.",
        // Use removal as the main name for the check, for familiarity with the javac flag.
        name = "removal",
        altNames = "DeprecatedForRemovalApiUsage")
public final class DeprecatedForRemovalApiUsage extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher,
                BugChecker.MemberReferenceTreeMatcher,
                BugChecker.MemberSelectTreeMatcher {

    private static final String MESSAGE_DETAILS =
            " - this will be removed in a future release and prevent library upgrades. Note: This error comes from "
                    + "the DeprecatedForRemovalApiUsage error-prone check, replacing the default-on java compiler flag "
                    + " '-Xlint:removal'. Use @SuppressWarnings(\"removal\") to suppress this error.";

    private static final Matcher<Tree> DEPRECATED_FOR_REMOVAL_SYMBOL =
            Matchers.symbolMatcher((symbol, state) -> symbol.isDeprecatedForRemoval());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    private Description checkTree(Tree tree, VisitorState state) {
        if (!DEPRECATED_FOR_REMOVAL_SYMBOL.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<String> qualifiedName = Optional.ofNullable(ASTHelpers.getSymbol(tree))
                .map(s ->
                        s.owner.getQualifiedName() + "#" + s.getQualifiedName().toString());
        String description = qualifiedName
                        .map(name -> String.format("%s is deprecated for removal", name))
                        .orElse("Deprecated-for-removal API usage")
                + MESSAGE_DETAILS;
        return buildDescription(tree).setMessage(description).build();
    }
}
