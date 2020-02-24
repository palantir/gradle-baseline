/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.AbstractReturnValueIgnored;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.List;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ReadReturnValueIgnored",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        providesFix = BugPattern.ProvidesFix.REQUIRES_HUMAN_ATTENTION,
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "The result of a read call must be checked to know if EOF has been reached or the expected number "
                + "of bytes have been consumed.")
public final class ReadReturnValueIgnored extends AbstractReturnValueIgnored {

    // MethodMatchers does not support matching arrays
    private static final Matcher<ExpressionTree> INPUT_STREAM_BUFFER_READ_MATCHER = Matchers.allOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(InputStream.class.getName())
                    .named("read"),
            Matchers.not(MethodMatchers.instanceMethod()
                    .onDescendantOf(InputStream.class.getName())
                    .named("read")
                    .withParameters()));

    private static final Matcher<ExpressionTree> RAF_BUFFER_READ_MATCHER = Matchers.allOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOf(RandomAccessFile.class.getName())
                    .named("read"),
            Matchers.not(MethodMatchers.instanceMethod()
                    .onDescendantOf(RandomAccessFile.class.getName())
                    .named("read")
                    .withParameters()));

    private static final Matcher<ExpressionTree> READER_SKIP_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(Reader.class.getName())
            .named("skip")
            .withParameters(long.class.getName());

    private static final Matcher<ExpressionTree> INPUT_STREAM_SKIP_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(InputStream.class.getName())
            .named("skip")
            .withParameters(long.class.getName());

    private static final Matcher<ExpressionTree> RAF_SKIP_MATCHER = MethodMatchers.instanceMethod()
            .onDescendantOf(RandomAccessFile.class.getName())
            .named("skipBytes")
            .withParameters(int.class.getName());

    private static final Matcher<ExpressionTree> MATCHER = Matchers.anyOf(
            MethodMatchers.instanceMethod()
                    .onDescendantOfAny(
                            RandomAccessFile.class.getName(), Reader.class.getName(), InputStream.class.getName())
                    .named("read"),
            INPUT_STREAM_SKIP_MATCHER,
            RAF_SKIP_MATCHER,
            READER_SKIP_MATCHER);

    @Override
    public Matcher<? super ExpressionTree> specializedMatcher() {
        return MATCHER;
    }

    @Override
    public Description describeReturnValueIgnored(MethodInvocationTree methodInvocationTree, VisitorState state) {
        Description result = super.describeReturnValueIgnored(methodInvocationTree, state);
        if (Description.NO_MATCH.equals(result)) {
            return result;
        }
        if (INPUT_STREAM_BUFFER_READ_MATCHER.matches(methodInvocationTree, state)) {
            return buildDescription(methodInvocationTree)
                    .addFix(replaceWithStatic(methodInvocationTree, state, ByteStreams.class.getName() + ".readFully"))
                    .build();
        }
        if (INPUT_STREAM_SKIP_MATCHER.matches(methodInvocationTree, state)) {
            return buildDescription(methodInvocationTree)
                    .addFix(replaceWithStatic(methodInvocationTree, state, ByteStreams.class.getName() + ".skipFully"))
                    .build();
        }
        if (RAF_BUFFER_READ_MATCHER.matches(methodInvocationTree, state)) {
            return buildDescription(methodInvocationTree)
                    .addFix(MoreSuggestedFixes.renameMethodInvocation(methodInvocationTree, "readFully", state))
                    .build();
        }
        if (READER_SKIP_MATCHER.matches(methodInvocationTree, state)) {
            return buildDescription(methodInvocationTree)
                    .addFix(replaceWithStatic(methodInvocationTree, state, CharStreams.class.getName() + ".skipFully"))
                    .build();
        }
        return describeMatch(methodInvocationTree);
    }

    // The old invocation target is used as the first argument of the new static invocation
    private static Optional<SuggestedFix> replaceWithStatic(
            MethodInvocationTree tree, VisitorState state, String fullyQualifiedReplacement) {
        Tree methodSelect = tree.getMethodSelect();
        if (!(methodSelect instanceof MemberSelectTree)) {
            return Optional.empty();
        }
        CharSequence sourceCode = state.getSourceCode();
        if (sourceCode == null) {
            return Optional.empty();
        }
        MemberSelectTree memberSelectTree = (MemberSelectTree) methodSelect;
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualifiedReference = MoreSuggestedFixes.qualifyType(state, fix, fullyQualifiedReplacement);
        CharSequence args = sourceCode.subSequence(
                state.getEndPosition(methodSelect) + 1, state.getEndPosition(lastItem(tree.getArguments())));
        fix.replace(
                tree,
                qualifiedReference
                        + '('
                        + state.getSourceForNode(memberSelectTree.getExpression())
                        + ", "
                        + args
                        + ')');
        return Optional.of(fix.build());
    }

    private static <T> T lastItem(List<T> items) {
        Preconditions.checkState(!items.isEmpty(), "List must not be empty");
        return items.get(items.size() - 1);
    }
}
