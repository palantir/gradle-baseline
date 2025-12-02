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
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
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
import java.util.logging.Level;
import java.util.logging.Logger;
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
                BugChecker.MemberSelectTreeMatcher,
                BugChecker.IdentifierTreeMatcher {

    private static final Logger log = Logger.getLogger(AbstractDeprecatedApiCheck.class.getName());

    /**
     * Returns true if the given symbol is deprecated in a way that should trigger this check.
     */
    protected abstract boolean isDeprecationWarning(Symbol symbol);

    /**
     * Returns true if the enclosing context should suppress this warning.
     */
    protected abstract boolean isEnclosingDeprecatedForSuppression(Symbol symbol);

    /**
     * Returns the error description to show in the diagnostic, using the qualified name of the deprecated symbol.
     */
    protected abstract String getErrorDescription(String qualifiedName);

    @Override
    public final Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public final Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public final Description matchIdentifier(IdentifierTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    @Override
    public final Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        return checkTree(tree, state);
    }

    private Description checkTree(Tree tree, VisitorState state) {
        if (isImportStatement(state)) {
            // We don't want to flag import statements, as those cannot be suppressed.
            return Description.NO_MATCH;
        }

        Symbol symbol = ASTHelpers.getSymbol(tree);

        if (symbol == null) {
            return Description.NO_MATCH;
        }

        if (!isDeprecationWarning(symbol)) {
            return Description.NO_MATCH;
        }

        if (isEnclosingDeprecated(state)) {
            // Suppress this warning if the enclosing method or class is deprecated.
            return Description.NO_MATCH;
        }

        // Note: Symbol#enclClass() returns the class itself if symbol is a class, rather than
        //   the potentially enclosing class (for nested classes). This is what we want here.
        Optional<ClassSymbol> owningClass = Optional.ofNullable(symbol.enclClass());
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
        if (classFileUri.isPresent()
                && isRegularFileOnSystem(classFileUri.get())
                && classFileUri.get().getPath().contains("/classes")) {
            // If the class file is a regular file on the local file system, and is within a /classes/ directory,
            //   this means we're calling a deprecated API within the same repository, even though maybe not the
            //   same project. We don't need to flag these usages, as breaks would be caught at compile time anyway.
            return Description.NO_MATCH;
        }

        String qualifiedName = symbol.owner.getQualifiedName() + "#"
                + symbol.getQualifiedName().toString();
        String description = getErrorDescription(qualifiedName);
        return buildDescription(tree).setMessage(description).build();
    }

    private boolean isImportStatement(VisitorState state) {
        return ASTHelpers.findEnclosingNode(state.getPath(), ImportTree.class) != null;
    }

    /**
     * Returns true if any of the enclosing nodes (methods/classes/etc) is deprecated
     *   (in a way that should suppress this warning).
     */
    private boolean isEnclosingDeprecated(VisitorState state) {
        for (Tree parent : state.getPath()) {
            if (!(parent instanceof MethodTree || parent instanceof ClassTree)) {
                // Only check for deprecation on methods and classes/interfaces/records/etc
                continue;
            }
            Symbol symbol = ASTHelpers.getSymbol(parent);
            if (symbol == null) {
                continue;
            }
            if (isEnclosingDeprecatedForSuppression(symbol)) {
                return true;
            }
        }
        return false;
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
            log.log(Level.WARNING, "Failed to check if URI is a regular file on the system: " + uri, e);
            return false;
        }
    }
}
