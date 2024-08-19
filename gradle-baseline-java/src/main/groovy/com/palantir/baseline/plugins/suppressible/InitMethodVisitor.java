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

package com.palantir.baseline.plugins.suppressible;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class InitMethodVisitor extends MethodVisitor {
    protected InitMethodVisitor(int api, MethodVisitor methodVisitor) {
        super(api, methodVisitor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Modify the BugChecker superclass constructor call to call the new superclass constructor
        if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name) && owner.equals(Suppressiblify.BUG_CHECKER)) {
            super.visitMethodInsn(opcode, Suppressiblify.SUPPRESSIBLE_BUG_CHECKER, name, descriptor, isInterface);
        } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
