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
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import java.io.Serial;
import java.util.List;
import org.jspecify.annotations.Nullable;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        summary = "String.format with only %s placeholders should use string concatenation instead",
        explanation = "When String.format only contains %s placeholders, direct string concatenation "
                + "with the + operator is more readable and performant. String.format adds unnecessary "
                + "overhead for simple string concatenation. See JEP 280 Indify String Concatenation "
                + "https://openjdk.org/jeps/280",
        severity = SeverityLevel.WARNING,
        tags = BugPattern.StandardTags.PERFORMANCE)
public final class UnnecessaryStringFormat extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STRING_FORMAT =
            MethodMatchers.staticMethod().onClass("java.lang.String").named("format");
    private static final Supplier<@Nullable Type> JAVA_UTIL_LOCALE =
            VisitorState.memoize(vs -> vs.getTypeFromString("java.util.Locale"));

    @Override
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!STRING_FORMAT.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();

        // Need at least a format string
        if (args.isEmpty()) {
            return Description.NO_MATCH;
        }

        // Handle both String.format(String, Object...) and String.format(Locale, String, Object...)
        int formatStringIndex = 0;
        int firstArgIndex = 1;

        // Check if first argument is a Locale
        if (args.size() > 1
                && ASTHelpers.isSameType(ASTHelpers.getType(args.get(0)), JAVA_UTIL_LOCALE.get(state), state)) {
            formatStringIndex = 1;
            firstArgIndex = 2;
        }

        // Format string must be a compile-time constant
        ExpressionTree formatStringArg = args.get(formatStringIndex);
        if (!(formatStringArg instanceof LiteralTree literalTree)) {
            return Description.NO_MATCH;
        }

        Object value = literalTree.getValue();
        if (!(value instanceof String formatString)) {
            return Description.NO_MATCH;
        }

        // Check if format string has only %s placeholders
        if (!formatString.contains("%s") || args.size() <= firstArgIndex) {
            return Description.NO_MATCH;
        }

        // Count %s occurrences (excluding %%)
        int placeholderCount = countSupportedPlaceholders(formatString);
        int actualArgCount = args.size() - firstArgIndex;

        // If placeholder count doesn't match arg count, let it be (might be intentional or another error)
        if (placeholderCount != actualArgCount) {
            return Description.NO_MATCH;
        }

        // Build the suggested fix
        SuggestedFix fix = buildConcatenationFix(formatString, args, firstArgIndex, tree, state);

        return describeMatch(tree, fix);
    }

    private static int countSupportedPlaceholders(String formatString) {
        int count = 0;
        int index = 0;
        for (; index < formatString.length() - 1; index++) {
            char ch = formatString.charAt(index);
            if (ch == '%') {
                char next = formatString.charAt(index + 1);
                if (isSupportedFormatChar(next)) {
                    count++;
                    index++; // Skip the format type 's' or 'd'
                } else if (next == '%') {
                    index++; // Skip the second '%'
                }
            }
        }
        return count;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private static SuggestedFix buildConcatenationFix(
            String formatString,
            List<? extends ExpressionTree> args,
            int firstArgIndex,
            ExpressionTree tree,
            VisitorState state) {

        StringBuilder replacement = new StringBuilder("\"\"");
        int argIndex = firstArgIndex;
        boolean openString = false;
        int index = 0;
        for (; index < formatString.length(); index++) {
            char ch = formatString.charAt(index);

            if (ch == '%' && index + 1 < formatString.length()) {
                char next = formatString.charAt(index + 1);

                if (isSupportedFormatChar(next)) {
                    // Replace %s with the argument
                    ExpressionTree argTree = args.get(argIndex);
                    String node = state.getSourceForNode(argTree);
                    replacement.append(" + ").append(node);
                    argIndex++;
                    index++; // Skip the 's'
                } else if (next == '%') {
                    // %% becomes %
                    replacement.append(" + \"%\"");
                    index++; // Skip the second %
                } else {
                    // Other format specifier (shouldn't happen due to our checks)
                    replacement.append(" + \"").append(ch).append('"');
                }
            } else {
                // Regular character - accumulate into string literal
                if (index == 0
                        || (index >= 2
                                && formatString.charAt(index - 2) == '%'
                                && (isSupportedFormatChar(formatString.charAt(index - 1))
                                        || formatString.charAt(index - 1) == '%'))) {
                    // Start of a new literal section
                    replacement.append(" + \"");
                    openString = true;
                }

                // Escape special characters
                if (ch == '"') {
                    replacement.append("\\\"");
                } else if (ch == '\\') {
                    replacement.append("\\\\");
                } else if (ch == '\n') {
                    replacement.append("\\n");
                } else if (ch == '\r') {
                    replacement.append("\\r");
                } else if (ch == '\t') {
                    replacement.append("\\t");
                } else {
                    replacement.append(ch);
                }

                // Check if next char is a placeholder or end of string
                if (index + 1 >= formatString.length()
                        || (formatString.charAt(index + 1) == '%' && index + 2 < formatString.length())) {
                    if (openString) {
                        replacement.append('"');
                        openString = false;
                    }
                }
            }
        }

        String code = replacement.toString();
        return SuggestedFix.builder()
                .replace(startPosition(tree), state.getEndPosition(tree), code)
                .build();
    }

    private static boolean isSupportedFormatChar(char next) {
        // support string and
        return next == 's' || next == 'S' || next == 'd' || next == 'f';
    }

    private static int startPosition(ExpressionTree tree) {
        return ((DiagnosticPosition) tree).getStartPosition();
    }
}
