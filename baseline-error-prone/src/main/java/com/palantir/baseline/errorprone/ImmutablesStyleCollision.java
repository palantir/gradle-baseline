package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.ChildMultiMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.AnnotatedTypeTree;
import org.immutables.value.Value;

@AutoService(BugChecker.class)
@BugPattern(
        name = "ImmutablesStyleCollision",
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        severity = BugPattern.SeverityLevel.ERROR,
        summary = "Immutables @Value.Style inline annotation should not be present alongside a Style "
                + "meta-annotation, as there is no Style merging. You should either modify the "
                + "meta-annotation, add the meta-annotation's fields to your inline @Value.Style declaration.")
public final class ImmutablesStyleCollision extends BugChecker implements BugChecker.AnnotatedTypeTreeMatcher {
    private static final Matcher<AnnotatedTypeTree> INLINE_STYLE_ANNOTATION = Matchers.hasAnnotation(Value.Style.class);
    private static final Matcher<AnnotatedTypeTree> STYLE_META_ANNOTATION = Matchers.annotations(
            ChildMultiMatcher.MatchType.AT_LEAST_ONE,
            Matchers.allOf(
                    Matchers.not(Matchers.isSameType(Value.Style.class)), Matchers.isSubtypeOf(Value.Style.class)));
    private static final Matcher<AnnotatedTypeTree> MATCHER =
            Matchers.allOf(INLINE_STYLE_ANNOTATION, STYLE_META_ANNOTATION);

    @Override
    public Description matchAnnotatedType(AnnotatedTypeTree tree, VisitorState state) {
        if (MATCHER.matches(tree, state)) {
            return buildDescription(tree)
                    .setMessage("Immutable type cannot have both inline @Value.Style and meta-annotation applied")
                    .build();
        }
        return Description.NO_MATCH;
    }
}
