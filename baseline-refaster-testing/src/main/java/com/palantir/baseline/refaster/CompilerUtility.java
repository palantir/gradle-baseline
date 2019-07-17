package com.palantir.baseline.refaster;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.Context;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

class CompilerUtility {

    private CompilerUtility() {}

    static CompilerResult compile(JavaFileObject javaFileObject) {
        JavaCompiler compiler = JavacTool.create();
        DiagnosticCollector<JavaFileObject> diagnosticsCollector = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnosticsCollector, Locale.ENGLISH, StandardCharsets.UTF_8);

        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(
                CharStreams.nullWriter(),
                fileManager,
                diagnosticsCollector,
                ImmutableList.of(),
                null,
                ImmutableList.of(javaFileObject));

        Iterable<? extends CompilationUnitTree> trees;
        try {
            trees = task.parse();
            task.analyze();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new CompilerResult() {
            @Override
            public Context context() {
                return task.getContext();
            }

            @Override
            public List<CompilationUnitTree> compilationUnits() {
                return ImmutableList.copyOf(trees);
            }

            @Override
            public List<Diagnostic<? extends JavaFileObject>> diagnostics() {
                return diagnosticsCollector.getDiagnostics();
            }
        };
    }

    interface CompilerResult {

        Context context();

        List<CompilationUnitTree> compilationUnits();

        List<Diagnostic<? extends JavaFileObject>> diagnostics();

    }

}
