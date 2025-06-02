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
            .onDescendantOf(XML_MAPPER + ".Builder")
            .named("build");

    private static final Supplier<Type> XML_INPUT_TYPE = Suppliers.typeFromString(XML_INPUT_FACTORY);
    private static final Supplier<Type> XML_OUTPUT_TYPE = Suppliers.typeFromString(XML_OUTPUT_FACTORY);

    private static final String SAFE_FACTORY_CTOR = "new WstxInputFactory(), new WstxOutputFactory()";
    private static final String SAFE_FACTORY_IMPORT1 = "com.ctc.wstx.stax.WstxInputFactory";
    private static final String SAFE_FACTORY_IMPORT2 = "com.ctc.wstx.stax.WstxOutputFactory";
    private static final String XML_FACTORY_IMPORT = "com.fasterxml.jackson.dataformat.xml.XmlFactory";

    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (XML_MAPPER_DEFAULT_CTOR.matches(tree, state)) {
            return buildAndDescribeFix(tree, "new XmlMapper(" + SAFE_FACTORY_CTOR + ")", true, null, state);
        }
        if (XML_MAPPER_ANY_CTOR.matches(tree, state) && !hasSafeFactory(tree.getArguments(), state)) {
            return tryInlineUnsafeFactoryCtor(tree, tree.getArguments(), state);
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
                .filter(MethodInvocationTree.class::isInstance)
                .map(MethodInvocationTree.class::cast)
                .filter(mit -> XML_MAPPER_BUILDER.matches(mit, state))
                .findFirst();

        if (builderCallOpt.isEmpty()) {
            return buildDescription(tree).build();
        }

        MethodInvocationTree builderCall = builderCallOpt.get();
        if (hasSafeFactory(builderCall.getArguments(), state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = builderCall.getArguments();
        if (args.isEmpty()) {
            return buildAndDescribeFix(
                    builderCall, "XmlMapper.builder(new XmlFactory(" + SAFE_FACTORY_CTOR + "))", false, null, state);
        } else if (args.size() == 1) {
            return tryInlineUnsafeFactoryBuilder(builderCall, args, state);
        }
        return buildDescription(tree).build();
    }

    // --- Helpers ---

    private Description tryInlineUnsafeFactoryCtor(
            NewClassTree tree, List<? extends ExpressionTree> args, VisitorState state) {
        ExpressionTree arg = args.size() == 1 ? args.get(0) : null;
        if (arg == null) {
            return Description.NO_MATCH;
        }

        if (arg instanceof NewClassTree nct
                && XML_FACTORY_ANY_CTOR.matches(nct, state)
                && nct.getArguments().isEmpty()) {
            return buildAndDescribeFix(tree, "new XmlMapper(" + SAFE_FACTORY_CTOR + ")", true, null, state);
        }

        if (arg instanceof IdentifierTree id && ASTHelpers.getSymbol(id) instanceof VarSymbol var) {
            ExpressionTree init = variableInitializer(var, state);
            if (init instanceof NewClassTree nct
                    && XML_FACTORY_ANY_CTOR.matches(nct, state)
                    && nct.getArguments().isEmpty()) {
                return buildAndDescribeFix(tree, "new XmlMapper(" + SAFE_FACTORY_CTOR + ")", true, var, state);
            }
        }
        return Description.NO_MATCH;
    }

    private Description tryInlineUnsafeFactoryBuilder(
            MethodInvocationTree call, List<? extends ExpressionTree> args, VisitorState state) {
        ExpressionTree arg = args.get(0);
        if (arg instanceof NewClassTree nct
                && XML_FACTORY_ANY_CTOR.matches(nct, state)
                && nct.getArguments().isEmpty()) {
            return buildAndDescribeFix(nct, "new XmlFactory(" + SAFE_FACTORY_CTOR + ")", false, null, state);
        }
        if (arg instanceof IdentifierTree id && ASTHelpers.getSymbol(id) instanceof VarSymbol var) {
            ExpressionTree init = variableInitializer(var, state);
            if (init instanceof NewClassTree nct
                    && XML_FACTORY_ANY_CTOR.matches(nct, state)
                    && nct.getArguments().isEmpty()) {
                return buildAndDescribeFix(
                        call, "XmlMapper.builder(new XmlFactory(" + SAFE_FACTORY_CTOR + "))", false, var, state);
            }
        }
        return Description.NO_MATCH;
    }

    private Description buildAndDescribeFix(
            Tree replaceTree,
            String replacement,
            boolean removeXmlFactoryImport,
            VarSymbol deleteVar,
            VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        fix.addImport(SAFE_FACTORY_IMPORT1);
        fix.addImport(SAFE_FACTORY_IMPORT2);
        if (removeXmlFactoryImport) {
            fix.removeImport(XML_FACTORY_IMPORT);
        } else {
            fix.addImport(XML_FACTORY_IMPORT);
        }
        fix.replace(replaceTree, replacement);
        if (deleteVar != null) {
            findVariableDeclaration(deleteVar, state).ifPresent(fix::delete);
        }
        return buildDescription(replaceTree).addFix(fix.build()).build();
    }

    private boolean hasSafeFactory(List<? extends ExpressionTree> args, VisitorState state) {
        return args.stream().anyMatch(arg -> isFactorySafe(arg, state));
    }

    private boolean isFactorySafe(ExpressionTree tree, VisitorState state) {
        Type type = ASTHelpers.getType(tree);
        if (type != null
                && (state.getTypes().isAssignable(type, XML_INPUT_TYPE.get(state))
                        || state.getTypes().isAssignable(type, XML_OUTPUT_TYPE.get(state)))) {
            return true;
        }

        if (tree instanceof NewClassTree nct && XML_FACTORY_ANY_CTOR.matches(tree, state)) {
            return !nct.getArguments().isEmpty() && hasSafeFactory(nct.getArguments(), state);
        }
        if (tree instanceof IdentifierTree id && ASTHelpers.getSymbol(id) instanceof VarSymbol var) {
            ExpressionTree init = variableInitializer(var, state);
            // Avoid infinite recursion
            return init != null && init != tree && isFactorySafe(init, state);
        }
        return false;
    }

    private Tree getPreviousInChain(Tree current) {
        return current instanceof MemberSelectTree mst ? mst.getExpression() : null;
    }

    private static ExpressionTree variableInitializer(VarSymbol var, VisitorState state) {
        return Stream.of(
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
                .map(VariableTree::getInitializer)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Optional<VariableTree> findVariableDeclaration(VarSymbol var, VisitorState state) {
        return Stream.of(
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
                .findFirst();
    }
}
