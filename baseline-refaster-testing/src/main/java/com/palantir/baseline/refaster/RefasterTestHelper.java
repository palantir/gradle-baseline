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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.apply.DescriptionBasedDiff;
import com.google.errorprone.apply.ImportOrganizer;
import com.google.errorprone.apply.SourceFile;
import com.google.errorprone.refaster.RefasterRuleBuilderScanner;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;

public final class RefasterTestHelper {

    private final List<CodeTransformer> transformers;

    /**
     * The source code of the given refaster rule should exist in {@code src/main/java}.
     */
    public static RefasterTestHelper forRefactoring(Class<?> refasterRuleClass) {
        return new RefasterTestHelper(refasterRuleClass);
    }

    private RefasterTestHelper(Class<?> refasterRuleClass) {
        Path sourceFile = Paths
                .get("src/main/java")
                .resolve(refasterRuleClass.getName().replaceAll("\\.", File.separator) + ".java");
        try {
            Iterable<String> sourceLines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
            this.transformers = extractRefasterRules(
                    JavaFileObjects.forSourceLines(refasterRuleClass.getName(), sourceLines));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RefactoringTestInput withInputLines(String fullyQualifiedName, String... lines) {
        return new RefactoringTestInput(transformers, JavaFileObjects.forSourceLines(fullyQualifiedName, lines));
    }

    public final class RefactoringTestInput {

        private List<CodeTransformer> transformers;
        private JavaFileObject input;

        RefactoringTestInput(List<CodeTransformer> transformers, JavaFileObject input) {
            this.transformers = transformers;
            this.input = input;
        }

        public void hasOutputLines(String... lines) {
            CompilerUtility.CompilerResult result = CompilerUtility.compile(input);
            Assertions.assertThat(result.diagnostics()).isEmpty();

            JCTree.JCCompilationUnit tree = result.compilationUnits().stream()
                    .filter(compilationUnitTree -> compilationUnitTree instanceof JCTree.JCCompilationUnit)
                    .map(compilationUnitTree -> (JCTree.JCCompilationUnit) compilationUnitTree)
                    .findFirst()
                    .orElseThrow(() -> new SafeIllegalArgumentException("Failed to compile input lines"));

            DescriptionBasedDiff diff = DescriptionBasedDiff.create(tree, ImportOrganizer.STATIC_FIRST_ORGANIZER);
            transformers.forEach(transformer -> transformer.apply(new TreePath(tree), result.context(), diff));

            SourceFile inputSourceFile = sourceFile(input);
            diff.applyDifferences(inputSourceFile);

            Assertions.assertThat(inputSourceFile.getSourceText()).isEqualTo(Joiner.on('\n').join(lines));
        }
    }

    private static SourceFile sourceFile(JavaFileObject javaFileObject) {
        try {
            return SourceFile.create(javaFileObject);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<CodeTransformer> extractRefasterRules(JavaFileObject object) {
        CompilerUtility.CompilerResult result = CompilerUtility.compile(object);
        ClassTree classTree = result.compilationUnits().stream()
                .flatMap(compilationUnitTree -> compilationUnitTree.getTypeDecls().stream())
                .filter(tree -> tree instanceof ClassTree)
                .map(tree -> (ClassTree) tree)
                .findFirst()
                .orElseThrow(() -> new SafeIllegalArgumentException("No class found in Refaster rule"));

        return ImmutableList.copyOf(RefasterRuleBuilderScanner.extractRules(classTree, result.context()));
    }

}
