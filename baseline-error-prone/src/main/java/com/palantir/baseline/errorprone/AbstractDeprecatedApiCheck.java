/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone;

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.tools.FileObject;

/**
 * This is an abstract base class for checks meant to replace the `-Xlint:deprecation` and `-Xlint:removal` compiler
 *   flags.
 *
 * See {@link DeprecatedApiUsage} and {@link DeprecatedForRemovalApiUsage} for concrete implementations.
 */
public abstract class AbstractDeprecatedApiCheck extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher,
                BugChecker.MemberReferenceTreeMatcher,
                BugChecker.MemberSelectTreeMatcher {

    protected abstract boolean isDeprecationWarning(Tree tree, VisitorState state);

    protected abstract String getErrorDescription(Optional<String> qualifiedName);

    @Override
    public final Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public final Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public final Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        if (isImportStatement(state)) {
            // We don't want to flag import statements, as those cannot be suppressed.
            return Description.NO_MATCH;
        }

        return checkTree(tree, state);
    }

    private Description checkTree(Tree tree, VisitorState state) {
        if (!isDeprecationWarning(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<Symbol> symbol = Optional.ofNullable(ASTHelpers.getSymbol(tree));

        Optional<ClassSymbol> owningClass = getOwningClass(symbol);
        Optional<URI> sourceFileUri = owningClass.map(c -> c.sourcefile).map(FileObject::toUri);
        if (sourceFileUri.isPresent() && isRegularFileOnSystem(sourceFileUri.get())) {
            // If the source file is a regular file on the local file system, this means we're calling a deprecated API
            //   within the same project. We don't want to flag these usages, as they don't have any impact, and any
            //   ABI break would have to be fixed immediately anyway.
            // Note: This isn't triggered by files within the same repo for error-prone tests, because these use
            //   in-memory file systems.
            return Description.NO_MATCH;
        }

        // Note: the logic below will NOT work if the target dependency applies the "java" plugin rather than
        //   the "java-library" plugin, because in that case the classfile that we get here will be within the
        //   jar file itself, which makes it particularly tricky to distinguish from just regular jar dependencies.
        // This should however be good enough for well-behaved repositories.
        Optional<URI> classFileUri = owningClass.map(c -> c.classfile).map(FileObject::toUri);
        if (classFileUri.isPresent() && isRegularFileOnSystem(classFileUri.get())) {
            if (classFileUri.get().getPath().contains("/classes")) {
                // If the class file is a regular file on the local file system, and is within a /classes/ directory,
                //   this means we're calling a deprecated API within the same repository, even though maybe not the
                //   same project. We don't need to flag these usages, as breaks would be caught at compile time anyway.
                return Description.NO_MATCH;
            }
        }

        Optional<String> qualifiedName = symbol.map(
                s -> s.owner.getQualifiedName() + "#" + s.getQualifiedName().toString());
        String description = getErrorDescription(qualifiedName);
        return buildDescription(tree).setMessage(description).build();
    }

    private boolean isImportStatement(VisitorState state) {
        return ASTHelpers.findEnclosingNode(state.getPath(), ImportTree.class) != null;
    }

    private Optional<ClassSymbol> getOwningClass(Optional<Symbol> symbol) {
        if (symbol.isEmpty()) {
            return Optional.empty();
        }
        Symbol owner = symbol.get().owner;
        while (owner != null && !(owner instanceof ClassSymbol)) {
            owner = owner.owner;
        }
        return Optional.ofNullable((ClassSymbol) owner);
    }

    /**
     * Returns true if the given URI points to a regular file on the local file system, as opposed to e.g.
     *   not an actual file, or a file within a zip/jar file system.
     */
    private boolean isRegularFileOnSystem(URI uri) {
        if (!"file".equals(uri.getScheme())) {
            return false;
        }

        try {
            Path path = Paths.get(uri);

            // Ensure we're using the default file system (not a zip file system, etc.)
            FileSystem fileSystem = path.getFileSystem();
            if (!fileSystem.equals(FileSystems.getDefault())) {
                return false;
            }

            // Check if it exists and is a regular file
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            return false;
        }
    }
}
