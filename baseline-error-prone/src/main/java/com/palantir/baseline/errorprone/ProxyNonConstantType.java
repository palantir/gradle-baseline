/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.reflect.Reflection;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Proxy instances should be created using constant types known at compile time to allow native-image "
                + "behavior to match hotspot. Methods which build proxies should take a "
                + "`Function<InvocationHandler, ? extends T>` instead of arbitrary class references. This check can "
                + "be safely suppressed in legacy code using @SuppressWarnings(\"ProxyNonConstantType\"). "
                + "The proxy annotation processor can make this process much easier: "
                + "https://github.com/palantir/proxy-processor\n"
                + "See https://www.graalvm.org/reference-manual/native-image/DynamicProxy/#automatic-detection")
public final class ProxyNonConstantType extends BaselineBugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> NEW_PROXY_INSTANCE_MATCHER =
            MethodMatchers.staticMethod().onClass(Proxy.class.getName()).named("newProxyInstance");

    private static final Matcher<ExpressionTree> REFLECTION_NEW_PROXY = MethodMatchers.staticMethod()
            .onClass(Reflection.class.getName())
            .named("newProxy")
            .withParameters(Class.class.getName(), InvocationHandler.class.getName());

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (REFLECTION_NEW_PROXY.matches(tree, state)) {
            return describeMatchWithFix(tree, state);
        }
        if (NEW_PROXY_INSTANCE_MATCHER.matches(tree, state)) {
            ExpressionTree interfaces = tree.getArguments().get(1);
            if (interfaces instanceof NewArrayTree) {
                NewArrayTree newArrayTree = (NewArrayTree) interfaces;
                for (ExpressionTree element : newArrayTree.getInitializers()) {
                    if (!isDirectClassAccess(element) && !TestCheckUtils.isTestCode(state)) {
                        return describeMatchWithFix(interfaces, state);
                    }
                }
            }
        }

        return Description.NO_MATCH;
    }

    private Description describeMatchWithFix(Tree tree, VisitorState state) {
        return buildDescription(tree)
                .addFix(SuggestedFixes.addSuppressWarnings(state, canonicalName()))
                .build();
    }

    private static boolean isDirectClassAccess(ExpressionTree expressionTree) {
        return expressionTree instanceof MemberSelectTree
                && ((MemberSelectTree) expressionTree).getIdentifier().contentEquals("class");
    }
}
