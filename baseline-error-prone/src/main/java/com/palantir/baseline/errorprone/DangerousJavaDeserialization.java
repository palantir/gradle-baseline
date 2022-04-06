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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import java.io.ObjectInput;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.ERROR,
        summary = "Disallow usage of Java's serialization features for security reasons, "
                + "cf. https://cwe.mitre.org/data/definitions/502.html")
public final class DangerousJavaDeserialization extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final Matcher<MethodTree> READ_OBJECT = Matchers.allOf(
            Matchers.methodIsNamed("readObject"),
            Matchers.methodHasParameters(Matchers.isSubtypeOf(ObjectInput.class.getName())));

    private static final Matcher<ExpressionTree> OBJECT_INPUT_READ_OBJECT = Matchers.allOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(ObjectInput.class.getName())
                    .named("readObject")
                    .withNoParameters(),
            Matchers.not(Matchers.enclosingMethod(READ_OBJECT)));

    private static final Matcher<ExpressionTree> LANG3_SERIALIZATION_UTILS_DESERIALIZE = MethodMatchers.staticMethod()
            .onClassAny(
                    "org.apache.commons.lang.SerializationUtils",
                    "org.apache.commons.lang3.SerializationUtils",
                    "org.springframework.util.SerializationUtils")
            .named("deserialize");

    private static final Matcher<ExpressionTree> DESERIALIZE =
            Matchers.anyOf(OBJECT_INPUT_READ_OBJECT, LANG3_SERIALIZATION_UTILS_DESERIALIZE);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {

        if (DESERIALIZE.matches(tree, state) && !TestCheckUtils.isTestCode(state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }
}
