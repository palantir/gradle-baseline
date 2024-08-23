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

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class SuppressifyingClassVisitor extends ClassVisitor {
    static final String SUPPRESSIBLE_BUG_CHECKER = "com/palantir/suppressibleerrorprone/SuppressibleBugChecker";
    static final String BUG_CHECKER = "com/google/errorprone/bugpatterns/BugChecker";

    private static final Pattern BUG_CHECKER_MATCHER_PATTERN =
            Pattern.compile("com/google/errorprone/bugpatterns/BugChecker\\$(?<className>\\w+)TreeMatcher");

    private String className;
    private boolean isBugCheckerWeWantToChange = false;
    private Set<String> matchMethodNames;

    SuppressifyingClassVisitor(int api, ClassVisitor classVisitor) {
        super(api, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;
        isBugCheckerWeWantToChange = !name.equals(SUPPRESSIBLE_BUG_CHECKER) && superName.equals(BUG_CHECKER);

        if (isBugCheckerWeWantToChange) {
            matchMethodNames = Arrays.stream(interfaces)
                    .flatMap(iface -> {
                        Matcher matcher = BUG_CHECKER_MATCHER_PATTERN.matcher(iface);
                        if (!matcher.matches()) {
                            return Stream.empty();
                        }
                        return Stream.of("match" + matcher.group("className"));
                    })
                    .collect(Collectors.toSet());
        }

        super.visit(
                version,
                access,
                name,
                signature,
                isBugCheckerWeWantToChange ? SUPPRESSIBLE_BUG_CHECKER : superName,
                interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        boolean matchMethodToChange = isBugCheckerWeWantToChange && matchMethodNames.contains(name);

        if (matchMethodToChange) {
            String originalImplNewName = name + "SuppressibleImpl";

            MethodVisitor newMatchMethod =
                    super.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, signature, exceptions);
            newMatchMethod.visitCode();
            // BugChecker matchMethods look like: Description matchMethod(MethodTree tree, VisitorState state)
            // We want to call:
            //     SuppressibleBugChecker.match(this, matchMethodSuppressibleImpl(tree, state), tree, state)
            // So first we load `this`, `tree` and `state`, then call matchMethodSuppressibleImpl
            int thisIndex = 0;
            int treeIndex = 1;
            int stateIndex = 2;

            newMatchMethod.visitVarInsn(Opcodes.ALOAD, thisIndex);
            newMatchMethod.visitVarInsn(Opcodes.ALOAD, treeIndex);
            newMatchMethod.visitVarInsn(Opcodes.ALOAD, stateIndex);
            newMatchMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, originalImplNewName, descriptor, false);

            // The Description returned from matchMethodSuppressibleImpl is on the stack, we need to add
            // `this`, `tree` and `state` again then invoke SuppressibleBugChecker.match
            newMatchMethod.visitVarInsn(Opcodes.ALOAD, thisIndex);
            newMatchMethod.visitVarInsn(Opcodes.ALOAD, treeIndex);
            newMatchMethod.visitVarInsn(Opcodes.ALOAD, stateIndex);
            // The stack size is now 4
            newMatchMethod.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    SUPPRESSIBLE_BUG_CHECKER,
                    "match",
                    "(Lcom/google/errorprone/matchers/Description;"
                            + "Lcom/google/errorprone/bugpatterns/BugChecker;"
                            + "Lcom/sun/source/tree/Tree;"
                            + "Lcom/google/errorprone/VisitorState;)"
                            + "Lcom/google/errorprone/matchers/Description;",
                    false);

            // We then return the Description from SuppressibleBugChecker.match
            newMatchMethod.visitInsn(Opcodes.ARETURN);

            // Max stack size is 4 (see above), max local variables is 3 (this, tree, state)
            newMatchMethod.visitMaxs(4, 3);
            newMatchMethod.visitEnd();

            return super.visitMethod(access, originalImplNewName, descriptor, signature, exceptions);
        }

        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (isBugCheckerWeWantToChange && "<init>".equals(name)) {
            return new InitMethodVisitor(Opcodes.ASM9, methodVisitor);
        }

        return methodVisitor;
    }
}
