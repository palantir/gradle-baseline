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

import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import com.google.errorprone.refaster.RefasterRuleBuilderScanner;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * TaskListener that receives compilation of a Refaster rule class and outputs a combined serialized analyzer
 * to the specified path.
 *
 * Based on {@link com.google.errorprone.refaster.RefasterRuleCompilerAnalyzer}.
 */
public final class BaselineRefasterCompilerAnalyzer implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(BaselineRefasterCompilerAnalyzer.class);
    private final Context context;
    private final Path destinationPath;

    private final List<CodeTransformer> rules = new ArrayList<>();

    BaselineRefasterCompilerAnalyzer(Context context, Path destinationPath) {
        this.context = context;
        this.destinationPath = destinationPath;
    }

    @Override
    @SuppressWarnings("StrictUnusedVariable")
    public void started(TaskEvent taskEvent) {}

    @Override
    public void finished(TaskEvent taskEvent) {
        if (taskEvent.getKind() != TaskEvent.Kind.ANALYZE) {
            return;
        }
        if (JavaCompiler.instance(context).errorCount() > 0) {
            return;
        }

        ClassTree tree = JavacTrees.instance(context).getTree(taskEvent.getTypeElement());
        if (tree == null) {
            return;
        }

        new TreeScanner<Void, Context>() {
            @Override
            public Void visitClass(ClassTree node, Context classContext) {
                try {
                    rules.addAll(RefasterRuleBuilderScanner.extractRules(node, classContext));
                } catch (RuntimeException | Error e) {
                    log.warn("Failed to extract rules", e);
                }
                return super.visitClass(node, classContext);
            }
        }.scan(tree, context);

        // On every taskFinished event, update the code transformer.
        // Only as of Java 10 is there a COMPILATION event that would show that the entire task had finished
        if (!rules.isEmpty()) {
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(destinationPath))) {
                output.writeObject(CompositeCodeTransformer.compose(rules));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
