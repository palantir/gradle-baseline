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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Prefer JDK `InputStream.transferTo(OutputStream)` over utility methods such as "
                + "`com.google.common.io.ByteStreams.copy(InputStream, OutputStream)`, "
                + "`org.apache.commons.io.IOUtils.copy(InputStream, OutputStream)`, "
                + "`org.apache.commons.io.IOUtils.copyLong(InputStream, OutputStream)` "
                + "see: https://github.com/palantir/gradle-baseline/issues/2615 ",
        explanation = "Allow for optimization when underlying input stream (such as `ByteArrayInputStream`,"
                + " `ChannelInputStream`) overrides `long transferTo(OutputStream)` to avoid extra array allocations and"
                + " copy larger chunks at a time (e.g. allowing 16KiB chunks via"
                + " `ApacheHttpClientBlockingChannel.ModulatingOutputStream` from #1790).\n\n"
                + "When running on JDK 21+, this also enables 16KiB byte chunk copies via"
                + " `InputStream.transferTo(OutputStream)` per [JDK-8299336](https://bugs.openjdk.org/browse/JDK-8299336),"
                + " where as on JDK < 21 and when using Guava `ByteStreams.copy` 8KiB byte chunk copies are used. \n\n"
                + "References:\n\n"
                + "  * https://github.com/palantir/hadoop-crypto/pull/586\n"
                + "  * https://bugs.openjdk.org/browse/JDK-8299336\n"
                + "  * https://bugs.openjdk.org/browse/JDK-8067661\n"
                + "  * https://bugs.openjdk.org/browse/JDK-8265891\n"
                + "  * https://bugs.openjdk.org/browse/JDK-8273038\n"
                + "  * https://bugs.openjdk.org/browse/JDK-8279283\n"
                + "  * https://bugs.openjdk.org/browse/JDK-8296431\n")
public final class PreferInputStreamTransferTo extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    private static final String ERROR_MESSAGE = "Prefer InputStream.transferTo(OutputStream)";

    public static final String INPUT_STREAM = "java.io.InputStream";
    private static final Matcher<Tree> INPUT_STREAM_MATCHER = MoreMatchers.isSubtypeOf(INPUT_STREAM);
    public static final String OUTPUT_STREAM = "java.io.OutputStream";
    private static final Matcher<Tree> OUTPUT_STREAM_MATCHER = MoreMatchers.isSubtypeOf(OUTPUT_STREAM);

    private static final Matcher<ExpressionTree> GUAVA_BYTE_STREAM_COPY_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.google.common.io.ByteStreams")
            .namedAnyOf("copy")
            .withParameters(INPUT_STREAM, OUTPUT_STREAM);
    private static final Matcher<ExpressionTree> APACHE_COMMONS_BYTE_STREAM_COPY_MATCHER = MethodMatchers.staticMethod()
            .onClass("org.apache.commons.io.IOUtils")
            .namedAnyOf("copy", "copyLarge")
            .withParameters(INPUT_STREAM, OUTPUT_STREAM);
    private static final Matcher<ExpressionTree> AWS_BYTE_STREAM_COPY_MATCHER = MethodMatchers.staticMethod()
            .onClass("com.amazonaws.util.IOUtils")
            .namedAnyOf("copy")
            .withParameters(INPUT_STREAM, OUTPUT_STREAM);

    private static final Matcher<ExpressionTree> METHOD_MATCHER = Matchers.anyOf(
            GUAVA_BYTE_STREAM_COPY_MATCHER, APACHE_COMMONS_BYTE_STREAM_COPY_MATCHER, AWS_BYTE_STREAM_COPY_MATCHER);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (METHOD_MATCHER.matches(tree, state)) {
            List<? extends ExpressionTree> args = tree.getArguments();
            if (args.size() != 2) {
                return Description.NO_MATCH;
            }

            ExpressionTree maybeInputStreamArg = args.get(0);
            ExpressionTree maybeOutputStreamArg = args.get(1);
            if (INPUT_STREAM_MATCHER.matches(maybeInputStreamArg, state)
                    && OUTPUT_STREAM_MATCHER.matches(maybeOutputStreamArg, state)) {
                String inputStreamArg = state.getSourceForNode(maybeInputStreamArg);
                String outputStreamArg = state.getSourceForNode(maybeOutputStreamArg);
                if (inputStreamArg == null || outputStreamArg == null) {
                    return Description.NO_MATCH;
                }

                // Avoid possible infinite recursion replacing with `this.transferTo(outputStream)`
                if (maybeInputStreamArg instanceof IdentifierTree
                        && ((IdentifierTree) maybeInputStreamArg).getName().contentEquals("this")) {
                    inputStreamArg = "super";
                }

                String replacement = inputStreamArg + ".transferTo(" + outputStreamArg + ")";
                SuggestedFix fix =
                        SuggestedFix.builder().replace(tree, replacement).build();
                return buildDescription(tree)
                        .setMessage(ERROR_MESSAGE)
                        .addFix(fix)
                        .build();
            }
        }

        return Description.NO_MATCH;
    }
}
