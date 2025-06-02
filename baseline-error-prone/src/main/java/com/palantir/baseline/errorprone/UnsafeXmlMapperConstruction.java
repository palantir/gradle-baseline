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
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        summary = "XmlMapper must be constructed with XMLInput/XMLOutput factories either directly or via XmlFactory. "
                + "This prevents XmlMapper discovering shaded version of woodstox and erroring.",
        severity = SeverityLevel.ERROR)
public final class UnsafeXmlMapperConstruction extends BugChecker
        implements BugChecker.NewClassTreeMatcher, BugChecker.MethodInvocationTreeMatcher {

    private static final String XML_FACTORY = "com.fasterxml.jackson.dataformat.xml.XmlFactory";
    private static final String XML_MAPPER = "com.fasterxml.jackson.dataformat.xml.XmlMapper";
    private static final String XML_INPUT_FACTORY = "javax.xml.stream.XMLInputFactory";
    private static final String XML_OUTPUT_FACTORY = "javax.xml.stream.XMLOutputFactory";

    private static final Matcher<ExpressionTree> XML_MAPPER_DEFAULT_CTOR =
            Matchers.constructor().forClass(XML_MAPPER).withParameters(Collections.emptyList());

    private static final Matcher<ExpressionTree> XML_MAPPER_ANY_CTOR =
            Matchers.constructor().forClass(XML_MAPPER);

    private static final Matcher<ExpressionTree> XML_FACTORY_ANY_CTOR =
            Matchers.constructor().forClass(XML_FACTORY);

    private static final Matcher<ExpressionTree> XML_MAPPER_BUILDER =
            MethodMatchers.staticMethod().onClass(XML_MAPPER).named("builder");

    private static final Matcher<ExpressionTree> XML_MAPPER_BUILDER_BUILD = MethodMatchers.instanceMethod()
            .onDescendantOf("com.fasterxml.jackson.dataformat.xml.XmlMapper.Builder")
            .named("build");

    private static final Supplier<Type> XML_INPUT_TYPE = Suppliers.typeFromString(XML_INPUT_FACTORY);
    private static final Supplier<Type> XML_OUTPUT_TYPE = Suppliers.typeFromString(XML_OUTPUT_FACTORY);

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (XML_MAPPER_DEFAULT_CTOR.matches(tree, state)) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            fix.addImport("com.ctc.wstx.stax.WstxInputFactory");
            fix.addImport("com.ctc.wstx.stax.WstxOutputFactory");
            fix.replace(tree, "new XmlMapper(new WstxInputFactory(), new WstxOutputFactory())");
            return buildDescription(tree).addFix(fix.build()).build();
        }

        if (XML_MAPPER_ANY_CTOR.matches(tree, state) && !hasSafeFactory(tree.getArguments(), state)) {
            List<? extends ExpressionTree> args = tree.getArguments();
            if (args.size() == 1) {
                ExpressionTree arg = args.get(0);

                // Inline new XmlFactory()
                if (arg instanceof NewClassTree nct
                        && XML_FACTORY_ANY_CTOR.matches(nct, state)
                        && nct.getArguments().isEmpty()) {
                    SuggestedFix.Builder fix = SuggestedFix.builder();
                    fix.addImport("com.ctc.wstx.stax.WstxInputFactory");
                    fix.addImport("com.ctc.wstx.stax.WstxOutputFactory");
                    fix.removeImport("com.fasterxml.jackson.dataformat.xml.XmlFactory");
                    fix.replace(tree, "new XmlMapper(new WstxInputFactory(), new WstxOutputFactory())");
                    return buildDescription(tree).addFix(fix.build()).build();
                }

                // Variable reference to unsafe XmlFactory
                if (arg instanceof IdentifierTree id && ASTHelpers.getSymbol(id) instanceof VarSymbol var) {
                    ExpressionTree init = variableInitializer(var, state);
                    if (init instanceof NewClassTree nct
                            && XML_FACTORY_ANY_CTOR.matches(nct, state)
                            && nct.getArguments().isEmpty()) {
                        SuggestedFix.Builder fix = SuggestedFix.builder();
                        fix.addImport("com.ctc.wstx.stax.WstxInputFactory");
                        fix.addImport("com.ctc.wstx.stax.WstxOutputFactory");
                        fix.removeImport("com.fasterxml.jackson.dataformat.xml.XmlFactory");
                        // Inline at the callsite
                        fix.replace(tree, "new XmlMapper(new WstxInputFactory(), new WstxOutputFactory())");
                        // Optionally remove the variable assignment for factory
                        Stream.of(
                                        Optional.ofNullable(state.findEnclosing(ClassTree.class))
                                                .map(ClassTree::getMembers)
                                                .orElseGet(Collections::emptyList),
                                        Optional.ofNullable(state.findEnclosing(MethodTree.class))
                                                .map(MethodTree::getBody)
                                                .map(BlockTree::getStatements)
                                                .orElseGet(Collections::emptyList))
                                .flatMap(List::stream)
                                .filter(VariableTree.class::isInstance)
                                .map(VariableTree.class::cast)
                                .filter(vt -> Objects.equals(ASTHelpers.getSymbol(vt), var))
                                .findFirst()
                                .ifPresent(fix::delete);

                        return buildDescription(tree).addFix(fix.build()).build();
                    }
                }
            }
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!XML_MAPPER_BUILDER_BUILD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<MethodInvocationTree> builderCallOpt = Stream.iterate(
                        tree.getMethodSelect(), Objects::nonNull, this::getPreviousInChain)
                .filter(MemberSelectTree.class::isInstance)
                .map(MemberSelectTree.class::cast)
                .map(MemberSelectTree::getExpression)
                .filter(expr -> expr instanceof MethodInvocationTree)
                .map(MethodInvocationTree.class::cast)
                .filter(mit -> XML_MAPPER_BUILDER.matches(mit, state))
                .findFirst();

        if (builderCallOpt.isPresent()) {
            MethodInvocationTree builderCall = builderCallOpt.get();
            if (hasSafeFactory(builderCall.getArguments(), state)) {
                return Description.NO_MATCH;
            }

            SuggestedFix.Builder fix = SuggestedFix.builder();
            fix.addImport("com.ctc.wstx.stax.WstxInputFactory");
            fix.addImport("com.ctc.wstx.stax.WstxOutputFactory");
            fix.addImport("com.fasterxml.jackson.dataformat.xml.XmlFactory");

            List<? extends ExpressionTree> args = builderCall.getArguments();
            if (args.isEmpty()) {
                // builder() → builder(new XmlFactory(new WstxInputFactory(), new WstxOutputFactory()))
                fix.replace(
                        builderCall,
                        "XmlMapper.builder(new XmlFactory(new WstxInputFactory(), new WstxOutputFactory()))");
                return buildDescription(builderCall).addFix(fix.build()).build();
            } else if (args.size() == 1) {
                ExpressionTree arg = args.get(0);
                // Inline new XmlFactory()
                if (arg instanceof NewClassTree nct
                        && XML_FACTORY_ANY_CTOR.matches(nct, state)
                        && nct.getArguments().isEmpty()) {
                    fix.replace(nct, "new XmlFactory(new WstxInputFactory(), new WstxOutputFactory())");
                    return buildDescription(builderCall).addFix(fix.build()).build();
                }
                // Variable reference to unsafe XmlFactory
                if (arg instanceof IdentifierTree id && ASTHelpers.getSymbol(id) instanceof VarSymbol var) {
                    ExpressionTree init = variableInitializer(var, state);
                    if (init instanceof NewClassTree nct
                            && XML_FACTORY_ANY_CTOR.matches(nct, state)
                            && nct.getArguments().isEmpty()) {
                        // Inline at the callsite
                        fix.replace(
                                builderCall,
                                "XmlMapper.builder(new XmlFactory(new WstxInputFactory(), new WstxOutputFactory()))");
                        // Optionally remove the variable assignment for factory
                        Stream.of(
                                        Optional.ofNullable(state.findEnclosing(ClassTree.class))
                                                .map(ClassTree::getMembers)
                                                .orElseGet(Collections::emptyList),
                                        Optional.ofNullable(state.findEnclosing(MethodTree.class))
                                                .map(MethodTree::getBody)
                                                .map(BlockTree::getStatements)
                                                .orElseGet(Collections::emptyList))
                                .flatMap(List::stream)
                                .filter(VariableTree.class::isInstance)
                                .map(VariableTree.class::cast)
                                .filter(vt -> Objects.equals(ASTHelpers.getSymbol(vt), var))
                                .findFirst()
                                .ifPresent(fix::delete);
                        return buildDescription(builderCall).addFix(fix.build()).build();
                    }
                }
            }
        }
        return buildDescription(tree).build();
    }

    private Tree getPreviousInChain(Tree current) {
        if (current instanceof MemberSelectTree mst) {
            return mst.getExpression();
        }
        return null;
    }

    private boolean hasSafeFactory(List<? extends ExpressionTree> args, VisitorState state) {
        return args.stream().anyMatch(arg -> isFactorySafe(arg, state));
    }

    /**
     * Determines if an expression represents a safe XML factory.
     * <p>
     * This method performs three checks in sequence:
     * <ol>
     *   <li>If the expression is directly assignable to XMLInputFactory or XMLOutputFactory</li>
     *   <li>If the expression is an XmlFactory constructor that includes at least one safe factory argument</li>
     *   <li>If the expression is a variable reference whose initializer resolves to a safe factory</li>
     * </ol>
     * <p>
     * The method handles variable resolution to trace factory configurations through variable references
     * and field declarations.
     *
     * @param tree The expression tree to check
     * @param state The visitor state for type checking and AST traversal
     * @return true if the expression represents a safe factory, false otherwise
     */
    private boolean isFactorySafe(ExpressionTree tree, VisitorState state) {
        // Check if it's a direct XML input/output factory
        Type type = ASTHelpers.getType(tree);
        if (type != null
                && (state.getTypes().isAssignable(type, XML_INPUT_TYPE.get(state))
                        || state.getTypes().isAssignable(type, XML_OUTPUT_TYPE.get(state)))) {
            return true;
        }

        // Check if it's an XmlFactory with safe arguments
        if (tree instanceof NewClassTree nct && XML_FACTORY_ANY_CTOR.matches(tree, state)) {
            return !nct.getArguments().isEmpty() && hasSafeFactory(nct.getArguments(), state);
        }

        // For variable references, try to resolve their initializers
        return tree instanceof IdentifierTree id
                && ASTHelpers.getSymbol(id) instanceof VarSymbol var
                && Optional.ofNullable(variableInitializer(var, state))
                        .filter(init -> init != tree)
                        .map(init -> isFactorySafe(init, state))
                        .orElse(false);
    }

    /**
     * Finds the initializer expression for a given variable symbol.
     * <p>
     * This method searches for variable declarations in both class scope (fields) and method
     * scope (local variables) that match the provided symbol. When found, it returns the
     * initializer expression of that variable.
     * <p>
     * The search is performed in the following order:
     * <ol>
     *   <li>Class member variables (fields)</li>
     *   <li>Local method variables</li>
     * </ol>
     *
     * @param var The variable symbol to find the initializer for
     * @param state The visitor state for accessing the current AST context
     * @return The initializer expression if found, or null if the variable has no initializer
     *         or couldn't be located in the current scope
     */
    private static ExpressionTree variableInitializer(VarSymbol var, VisitorState state) {
        return Stream.of(
                        // Class member variables
                        Optional.ofNullable(state.findEnclosing(ClassTree.class))
                                .map(ClassTree::getMembers)
                                .orElseGet(Collections::emptyList),

                        // Local method variables
                        Optional.ofNullable(state.findEnclosing(MethodTree.class))
                                .map(MethodTree::getBody)
                                .map(BlockTree::getStatements)
                                .orElseGet(Collections::emptyList))
                .flatMap(List::stream)
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .filter(vt -> Objects.equals(ASTHelpers.getSymbol(vt), var))
                .map(VariableTree::getInitializer)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
