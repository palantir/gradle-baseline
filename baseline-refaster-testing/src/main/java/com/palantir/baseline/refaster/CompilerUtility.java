/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

final class CompilerUtility {

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
        } catch (RuntimeException e) {
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
