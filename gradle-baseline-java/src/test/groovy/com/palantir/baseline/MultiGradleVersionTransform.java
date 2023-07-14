/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public final class MultiGradleVersionTransform implements ASTTransformation {
    @Override
    public void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        System.out.println("========================== TRANSFORM BEGIN ==========================");
        System.out.println(astNodes[1].getText());
        ASTNode testClass = astNodes[1];
        ((ClassNode) testClass)
                .getMethods()
                .forEach(methodNode -> System.out.println("methodNode.getName() = " + methodNode.getName()
                        + " params = " + methodNode.getParameters().length));
    }
}
