package com.palantir.baseline.errorprone;

import static java.util.Map.entry;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ExpressionStatementTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Description.Builder;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.StringLiteral;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        severity = BugPattern.SeverityLevel.WARNING,
        summary = "Variables, classes, methods, and string literals should use inclusive language")
public final class InclusiveLanguage extends BugChecker implements VariableTreeMatcher, ExpressionStatementTreeMatcher, NewClassTreeMatcher {
    Matcher<ExpressionTree> methodMatcher = Matchers.anyOf(Matchers.anyMethod());
    
    Matcher<ExpressionTree> stringMatcher = new StringLiteral(Pattern.compile("(.*?)"));
    
    private final String recommendation = "To convey the same idea, consider %s instead of %s.";
    
    private final Map<String, String> languageToFlag = Map.ofEntries(
            entry("ablebodied", "nondisabled"), 
            entry("blackout", "downtime"), 
            entry("blacklist", "denylist"), 
            entry("whitelist", "allowlist"),
            entry("dummy", "placeholder"), 
            entry("grandfather", "legacy"), 
            entry("guys", "folks"),
            entry("gypped", "swindled"),
            entry("handicap", "disabled"),
            entry("housekeeping", "maintenance"),
            entry("ladies", "women"),
            entry("master", "primary"),
            entry("slave", "standby"),
            entry("nittygritty", "core"),
            entry("sanitycheck", "check"),
            entry("sanity check", "check")
            );
    
    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        
        Stream<String> matches = languageToFlag.keySet().stream()
                .filter(key -> key.contains(tree.getName().toString()));
        
        if (matches.findAny().isPresent()) {
            Builder description = buildDescription(tree);
            matches.forEach(match -> description.addFix(
                    SuggestedFixes.renameVariable(tree, getInclusiveVersion(match), state));
            return description.build();
        }
        return Description.NO_MATCH;
    }
    
    @Override
    public Description matchExpressionStatement(ExpressionStatementTree tree, VisitorState state) {
        if (methodMatcher.matches(tree.getExpression(), state) && (tree.getExpression() instanceof MethodTree)) {
            MethodTree method = (MethodTree) tree;
            Stream<String> matches = languageToFlag.keySet().stream()
                        .filter(key -> key.contains(method.getName().toString()));
            if (matches.findAny().isPresent()) {
                Builder description = buildDescription(tree);
                matches.forEach(match -> description.addFix(
                            SuggestedFixes.renameMethodWithInvocations(method, getInclusiveVersion(match), state)));
                return description.build();
            }
        }
        
        if (stringMatcher.matches(tree.getExpression(), state) && tree.getExpression() instanceof LiteralTree) {
            Stream<String> matches = languageToFlag.keySet().stream()
                    .filter(key -> key.contains(tree.getExpression().toString()));
            if (matches.findAny().isPresent()) {
                Builder description = buildDescription(tree);
                matches.forEach(match -> description.addFix(SuggestedFix.builder()
                        .setShortDescription(String.format(recommendation, getInclusiveVersion(match), match))
                        .build()));
                return description.build();
            }
        }
        return Description.NO_MATCH;
    }
    
    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        Stream<String> matches = languageToFlag.keySet().stream()
                .filter(key -> key.contains(tree.getClass().getName()));
        if (matches.findAny().isPresent()) {
            Builder description = buildDescription(tree);
            matches.forEach(match -> description.addFix(SuggestedFix.builder()
                    .setShortDescription(String.format(recommendation, getInclusiveVersion(match), match))
                    .build()));
            return description.build();
        }
        return Description.NO_MATCH;
    }
    
    private String getInclusiveVersion(String problematicString) {
        return problematicString.replace(problematicString, languageToFlag.get(problematicString));
    }
}
