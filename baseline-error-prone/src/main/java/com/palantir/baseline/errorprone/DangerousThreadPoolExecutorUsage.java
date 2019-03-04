/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.IsSubtypeOf;
import com.google.errorprone.matchers.Matcher;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import java.util.concurrent.ThreadPoolExecutor;

@AutoService(BugChecker.class)
@BugPattern(
        name = "DangerousThreadPoolExecutorUsage",
        severity = SeverityLevel.ERROR,
        summary = "Disallow direct ThreadPoolExecutor usages.")
public final class DangerousThreadPoolExecutorUsage extends BugChecker implements BugChecker.NewClassTreeMatcher {
    private static final String ERROR_MESSAGE = "Should not normally use ThreadPoolExecutor directly. "
            + "ThreadPoolExecutor is a nuanced class. In our experience, when executors are configured "
            + "in non-default ways (i.e. the ones in Executors), we usually see bad behaviour. This check "
            + "is intended to be advisory - it's fine to @SuppressWarnings(\"DangerousThreadPoolExecutorUsage\") "
            + "in certain cases, but is usually not recommended. The most common bug is to set "
            + "corePoolSize != maxPoolSize and to have an unbounded or large work queue; the executor will never "
            + "grow beyond the corePoolSize. If you have questions here, feel free to ask around internally, or "
            + "read the source.";

    private static final String THREAD_POOL_EXECUTOR = ThreadPoolExecutor.class.getCanonicalName();
    private static final Matcher<ExpressionTree> matcher = new IsSubtypeOf<>(THREAD_POOL_EXECUTOR);

    private static final long serialVersionUID = 1L;

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (!matcher.matches(tree.getIdentifier(), state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).setMessage(ERROR_MESSAGE).build();
    }
}
