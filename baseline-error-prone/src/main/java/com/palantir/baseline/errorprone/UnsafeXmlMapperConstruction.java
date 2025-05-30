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
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Collections;
import java.util.List;

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
            return error(tree, "Do not use 'new XmlMapper()' without providing an input/output factory.");
        }

        if (XML_MAPPER_ANY_CTOR.matches(tree, state) && !argumentsContainSafeFactory(tree.getArguments(), state)) {
            return error(
                    tree,
                    "Do not use 'new XmlMapper(...)' unless at least one XMLInputFactory or XMLOutputFactory "
                            + "is supplied (directly or via XmlFactory).");
        }
        return Description.NO_MATCH;
    }

    // XmlMapper.builder(...).build()
    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!XML_MAPPER_BUILDER_BUILD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        MethodInvocationTree builderCall = findBuilderCall(tree, state);

        if (builderCall == null) {
            return error(tree, "Do not use 'XmlMapper.builder()' without configuring an input/output factory.");
        }

        if (!argumentsContainSafeFactory(builderCall.getArguments(), state)) {
            return error(
                    builderCall,
                    "Do not use 'XmlMapper.builder(...)' unless at least one XMLInputFactory or "
                            + "XMLOutputFactory is supplied (directly or via XmlFactory).");
        }
        return Description.NO_MATCH;
    }

    private Description error(Tree where, String msg) {
        return buildDescription(where).setMessage(msg).build();
    }

    private static boolean argumentsContainSafeFactory(List<? extends ExpressionTree> args, VisitorState state) {
        for (ExpressionTree arg : args) {
            if (isIoFactory(arg, state) || isSafeXmlFactory(arg, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIoFactory(ExpressionTree expr, VisitorState state) {
        Type type = ASTHelpers.getType(expr);
        if (type == null) {
            return false;
        }
        return state.getTypes().isAssignable(type, XML_INPUT_TYPE.get(state))
                || state.getTypes().isAssignable(type, XML_OUTPUT_TYPE.get(state));
    }

    private static boolean isSafeXmlFactory(ExpressionTree expr, VisitorState state) {
        // inline: new XmlFactory(...)
        if (expr instanceof NewClassTree nct && XML_FACTORY_ANY_CTOR.matches(expr, state)) {
            return argumentsContainSafeFactory(nct.getArguments(), state);
        }

        // reference: Variable, field, parameter
        if (expr instanceof IdentifierTree id) {
            VarSymbol var = ASTHelpers.getSymbol(id) instanceof VarSymbol v ? v : null;
            if (var != null) {
                ExpressionTree init = variableInitializer(var, state);
                if (init instanceof NewClassTree nct && XML_FACTORY_ANY_CTOR.matches(init, state)) {
                    return argumentsContainSafeFactory(nct.getArguments(), state);
                }
            }
        }
        return false;
    }

    private static ExpressionTree variableInitializer(VarSymbol var, VisitorState state) {

        ClassTree cls = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (cls != null) {
            for (Tree member : cls.getMembers()) {
                if (member instanceof VariableTree vt
                        && ASTHelpers.getSymbol(vt).equals(var)) {
                    return vt.getInitializer();
                }
            }
        }

        MethodTree method = ASTHelpers.findEnclosingNode(state.getPath(), MethodTree.class);
        if (method != null && method.getBody() != null) {
            for (StatementTree stmt : method.getBody().getStatements()) {
                if (stmt instanceof VariableTree vt && ASTHelpers.getSymbol(vt).equals(var)) {
                    return vt.getInitializer();
                }
            }
        }
        return null;
    }

    private static MethodInvocationTree findBuilderCall(MethodInvocationTree build, VisitorState state) {
        ExpressionTree cursor = build;
        while (cursor instanceof MethodInvocationTree mit) {
            if (XML_MAPPER_BUILDER.matches(mit, state)) {
                return mit;
            }
            ExpressionTree sel = mit.getMethodSelect();
            cursor = sel instanceof MemberSelectTree mst ? mst.getExpression() : null;
        }
        return null;
    }
}
