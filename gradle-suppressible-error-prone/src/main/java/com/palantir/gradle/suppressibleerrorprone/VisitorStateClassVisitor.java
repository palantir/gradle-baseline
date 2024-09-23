/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.suppressibleerrorprone;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class VisitorStateClassVisitor extends ClassVisitor {
    VisitorStateClassVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (name.equals("reportMatch")) {
            return new ReportMatchMethodVisitor(methodVisitor);
        }

        return methodVisitor;
    }

    private static final class ReportMatchMethodVisitor extends MethodVisitor {
        ReportMatchMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            // Load this aka VisitorState
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            // Load the first argument aka the Description
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // Modify the description using the method below. Result is on the stack.
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/palantir/suppressibleerrorprone/VisitorStateModifications",
                    "interceptDescription",
                    "(Lcom/google/errorprone/VisitorState;Lcom/google/errorprone/matchers/Description;)Lcom/google/errorprone/matchers/Description;",
                    false);
            // Move modified result from the stack back into the description parameter variable
            mv.visitVarInsn(Opcodes.ASTORE, 1);
        }
    }
}
