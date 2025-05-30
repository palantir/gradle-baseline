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

    // new XmlMapper(...)
    @Override
    public Description matchNewClass(NewClassTree tree, VisitorState state) {
        if (XML_MAPPER_DEFAULT_CTOR.matches(tree, state)) {
            return error(tree, ERR_NEW_DEFAULT);
        }

        if (XML_MAPPER_ANY_CTOR.matches(tree, state) && !containsSafeFactory(tree.getArguments(), state)) {
            return error(tree, ERR_NO_FACTORY);
        }
        return Description.NO_MATCH;
    }

    // XmlMapper.builder(...).build()
    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!XML_MAPPER_BUILDER_BUILD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<MethodInvocationTree> builderCall = findBuilderCall(tree, state);

        if (builderCall.isEmpty()) {
            return error(tree, ERR_BUILDER);
        }

        if (!containsSafeFactory(builderCall.get().getArguments(), state)) {
            return error(builderCall.get(), ERR_NO_FACTORY);
        }

        return Description.NO_MATCH;
    }

    private static boolean containsSafeFactory(List<? extends ExpressionTree> args, VisitorState state) {
        return args.stream().anyMatch(arg -> isIoFactory(arg, state) || isSafeXmlFactory(arg, state));
    }

    private static boolean isIoFactory(ExpressionTree expr, VisitorState state) {
        Type type = ASTHelpers.getType(expr);
        return type != null
                && (state.getTypes().isAssignable(type, XML_INPUT_TYPE.get(state))
                        || state.getTypes().isAssignable(type, XML_OUTPUT_TYPE.get(state)));
    }

    private static boolean isSafeXmlFactory(ExpressionTree expr, VisitorState state) {
        // inline: new XmlFactory(...)
        if (expr instanceof NewClassTree nct && XML_FACTORY_ANY_CTOR.matches(expr, state)) {
            return containsSafeFactory(nct.getArguments(), state);
        }

        // reference (variable/field/parameter)
        if (expr instanceof IdentifierTree id) {
            VarSymbol var = ASTHelpers.getSymbol(id) instanceof VarSymbol v ? v : null;
            ExpressionTree init = (var == null) ? null : variableInitializer(var, state);
            if (init instanceof NewClassTree nct && XML_FACTORY_ANY_CTOR.matches(init, state)) {
                return containsSafeFactory(nct.getArguments(), state);
            }
        }
        return false;
    }

    private static ExpressionTree variableInitializer(VarSymbol var, VisitorState state) {
        // Build a single stream containing all VariableTree nodes we care about
        Stream<VariableTree> variables = Stream.empty();

        ClassTree cls = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (cls != null) {
            variables = Stream.concat(
                    variables,
                    cls.getMembers().stream()
                            .filter(VariableTree.class::isInstance)
                            .map(VariableTree.class::cast));
        }

        MethodTree method = ASTHelpers.findEnclosingNode(state.getPath(), MethodTree.class);
        if (method != null && method.getBody() != null) {
            variables = Stream.concat(
                    variables,
                    method.getBody().getStatements().stream()
                            .filter(VariableTree.class::isInstance)
                            .map(VariableTree.class::cast));
        }

        // Find the first matching symbol, return its initializer (or null)
        return variables
                .filter(vt -> ASTHelpers.getSymbol(vt).equals(var))
                .map(VariableTree::getInitializer)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Optional<MethodInvocationTree> findBuilderCall(MethodInvocationTree build, VisitorState state) {

        return Stream.iterate(build, Objects::nonNull, UnsafeXmlMapperConstruction::receiverInvocation)
                .filter(mit -> XML_MAPPER_BUILDER.matches(mit, state))
                .findFirst();
    }

    private static MethodInvocationTree receiverInvocation(MethodInvocationTree current) {
        ExpressionTree select = current.getMethodSelect();
        if (select instanceof MemberSelectTree mst) {
            ExpressionTree receiver = mst.getExpression();
            return receiver instanceof MethodInvocationTree prev ? prev : null;
        }
        return null;
    }

    private Description error(Tree where, String message) {
        return buildDescription(where).setMessage(message).build();
    }
}
