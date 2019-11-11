/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DangerousStringInternUsage",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary =
                "Should not use String.intern(). Java string intern is complex and unpredictable. In most cases intern"
                    + " performs worse than pure-java implementations such as Guava Interners"
                    + " (https://guava.dev/releases/27.0.1-jre/api/docs/com/google/common/collect/Interners.html). If"
                    + " you are confident that String.intern is the correct tool, please make sure you fully"
                    + " understand the consequences.\n"
                    + "From https://shipilev.net/jvm/anatomy-quarks/10-string-intern/\n"
                    + "> For OpenJDK, String.intern() is the gateway to native JVM String table, and it comes with\n"
                    + "> caveats: throughput, memory footprint, pause time problems will await the users. It is very\n"
                    + "> easy to underestimate the impact of these caveats. Hand-rolled deduplicators/interners are\n"
                    + "> working much more reliably, because they are working on Java side, are just the regular"
                    + " Java\n"
                    + "> objects, generally better sized/resized, and also can be thrown away completely when not"
                    + " needed\n"
                    + "> anymore. GC-assisted String deduplication does alleviate things even more.\n"
                    + "> In almost every project we were taking care of, removing String.intern() from the hotpaths,"
                    + " \n"
                    + "> or optionally replacing it with a handrolled deduplicator, was the very profitable"
                    + " performance\n"
                    + "> optimization. Do not use String.intern() without thinking very hard about it, okay?")
public final class DangerousStringInternUsage extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STRING_INTERN_METHOD_MATCHER =
            MethodMatchers.instanceMethod().onExactClass(String.class.getName()).named("intern").withParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (STRING_INTERN_METHOD_MATCHER.matches(tree, state)) {
            return describeMatch(tree);
        }
        return Description.NO_MATCH;
    }
}
