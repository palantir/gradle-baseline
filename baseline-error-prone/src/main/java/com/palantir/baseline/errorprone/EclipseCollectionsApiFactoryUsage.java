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

package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ImportTree;
import com.sun.tools.javac.code.Type;

@AutoService(BugChecker.class)
@BugPattern(
        summary = "Import eclipse-collections uses api factory instead of impl factory. This could result in"
                + "classloading deadlocks as explained here: https://github.com/palantir/atlasdb/pull/7073",
        severity = BugPattern.SeverityLevel.ERROR,
        documentSuppression = false)
public final class EclipseCollectionsApiFactoryUsage extends BugChecker implements BugChecker.ImportTreeMatcher {
    private static final String BAD_ECLIPSE_COLLECTIONS_USAGE_SUBSTRING = "org.eclipse.collections.api.factory.";

    public EclipseCollectionsApiFactoryUsage() {}

    @Override
    public Description matchImport(ImportTree tree, VisitorState state) {
        Type importType = ASTHelpers.getType(tree.getQualifiedIdentifier());

        if (importType == null) {
            return Description.NO_MATCH;
        }

        String importName = importType.toString();
        if (importName.contains(BAD_ECLIPSE_COLLECTIONS_USAGE_SUBSTRING)) {
            String fixedImport = importName.replace(".api.", ".impl.");
            SuggestedFix fix = SuggestedFix.builder()
                    .removeImport(importName)
                    .addImport(fixedImport)
                    .build();
            return describeMatch(tree, fix);
        }
        return Description.NO_MATCH;
    }
}
