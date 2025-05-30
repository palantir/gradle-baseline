package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        summary = "XmlMapper must be constructed with an explicit XmlFactory or "
                + "XMLInput/XMLOutput factory for security and correctness.",
        severity = SeverityLevel.ERROR)
public final class UnsafeXmlMapperConstruction extends BugChecker
        implements BugChecker.NewClassTreeMatcher, BugChecker.MethodInvocationTreeMatcher {

    private static final String XML_FACTORY = "com.fasterxml.jackson.dataformat.xml.XmlFactory";
    private static final String XML_MAPPER = "com.fasterxml.jackson.dataformat.xml.XmlMapper";
    private static final String XML_INPUT_FACTORY = "javax.xml.stream.XMLInputFactory";
    private static final String XML_OUTPUT_FACTORY = "javax.xml.stream.XMLOutputFactory";

    private static final String ERR_NEW_DEFAULT =
            "Do not use 'new XmlMapper()' without providing an input/output factory.";
    private static final String ERR_NO_FACTORY =
            "Do not use 'new XmlMapper(...)' unless at least one XMLInputFactory or "
                    + "XMLOutputFactory is supplied (directly or via XmlFactory).";
    private static final String ERR_BUILDER =
            "Do not use 'XmlMapper.builder()' without configuring an input/output factory.";

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
            return buildDescription(tree).setMessage(ERR_NEW_DEFAULT).build();
        }

        if (XML_MAPPER_ANY_CTOR.matches(tree, state) && !hasSafeFactory(tree.getArguments(), state)) {
            return buildDescription(tree).setMessage(ERR_NO_FACTORY).build();
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!XML_MAPPER_BUILDER_BUILD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // Find the builder() call in the method chain
        ExpressionTree current = tree.getMethodSelect();
        while (current instanceof MemberSelectTree mst) {
            ExpressionTree expr = mst.getExpression();
            if (expr instanceof MethodInvocationTree mit && XML_MAPPER_BUILDER.matches(mit, state)) {
                // Found the builder call, check if it has safe factory args
                return hasSafeFactory(mit.getArguments(), state)
                        ? Description.NO_MATCH
                        : buildDescription(mit).setMessage(ERR_BUILDER).build();
            }
            if (!(expr instanceof MethodInvocationTree)) {
                break;
            }
            current = expr;
        }

        // Couldn't find the builder call or it wasn't safe
        return buildDescription(tree).setMessage(ERR_BUILDER).build();
    }

    private boolean hasSafeFactory(List<? extends ExpressionTree> args, VisitorState state) {
        return args.stream().anyMatch(arg -> isFactorySafe(arg, state));
    }

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
        if (tree instanceof IdentifierTree id) {
            VarSymbol var = ASTHelpers.getSymbol(id) instanceof VarSymbol v ? v : null;
            if (var != null) {
                ExpressionTree init = variableInitializer(var, state);
                if (init != null && init != tree) {
                    return isFactorySafe(init, state);
                }
            }
        }

        return false;
    }

    private static ExpressionTree variableInitializer(VarSymbol var, VisitorState state) {
        Stream<VariableTree> variables = Stream.empty();

        ClassTree cls = state.findEnclosing(ClassTree.class);
        if (cls != null) {
            variables = Stream.concat(
                    variables,
                    cls.getMembers().stream()
                            .filter(VariableTree.class::isInstance)
                            .map(VariableTree.class::cast));
        }

        MethodTree method = state.findEnclosing(MethodTree.class);
        if (method != null && method.getBody() != null) {
            variables = Stream.concat(
                    variables,
                    method.getBody().getStatements().stream()
                            .filter(VariableTree.class::isInstance)
                            .map(VariableTree.class::cast));
        }

        // Find matching variable and return its initializer
        return variables
                .filter(vt -> Objects.equals(ASTHelpers.getSymbol(vt), var))
                .map(VariableTree::getInitializer)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
