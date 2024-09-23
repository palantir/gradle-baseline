/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.suppressibleerrorprone;

import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.stream.Stream;

public final class VisitorStateModifications {
    @SuppressWarnings("RestrictedApi")
    public static Description interceptDescription(VisitorState visitorState, Description description) {
        if (description == Description.NO_MATCH) {
            return Description.NO_MATCH;
        }

        Tree firstSuppressibleParent = Stream.iterate(visitorState.getPath(), TreePath::getParentPath)
                .dropWhile(path -> !suppressibleKind(path.getLeaf().getKind()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find anything we can suppress"))
                .getLeaf();

        return Description.builder(
                        description.position,
                        description.checkName,
                        description.getLink(),
                        description.getMessageWithoutCheckName())
                .addFix(SuggestedFix.builder()
                        .prefixWith(
                                firstSuppressibleParent,
                                "@com.palantir.suppressibleerrorprone.RepeatableSuppressWarnings(\""
                                        + CommonConstants.AUTOMATICALLY_ADDED_PREFIX + description.checkName
                                        + "\")\n")
                        .build())
                .build();
    }

    private VisitorStateModifications() {}

    private static boolean suppressibleKind(Tree.Kind kind) {
        switch (kind) {
            case CLASS:
            case METHOD:
            case VARIABLE:
                // VARIABLE includes fields
                return true;
            default:
                return false;
        }
    }
}
