/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

public abstract class JarClassHasher implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private final Cache<ModuleVersionIdentifier, Result> cache =
            Caffeine.newBuilder().build();

    public static class Result {
        private final ImmutableSetMultimap<String, HashCode> hashesByClassName;

        private Result(ImmutableSetMultimap<String, HashCode> hashesByClassName) {
            this.hashesByClassName = hashesByClassName;
        }

        public ImmutableSetMultimap<String, HashCode> getHashesByClassName() {
            return hashesByClassName;
        }

        public static Result empty() {
            return new Result(ImmutableSetMultimap.of());
        }
    }

    public final Result hashClasses(ResolvedArtifact resolvedArtifact) {
        return cache.get(resolvedArtifact.getModuleVersion().getId(), _moduleId -> {
            File file = resolvedArtifact.getFile();
            if (!file.exists()) {
                return Result.empty();
            }

            ImmutableSetMultimap.Builder<String, HashCode> hashesByClassName = ImmutableSetMultimap.builder();
            try (FileInputStream fileInputStream = new FileInputStream(file);
                    JarInputStream jarInputStream = new JarInputStream(fileInputStream)) {
                JarEntry entry;
                while ((entry = jarInputStream.getNextJarEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }

                    if (entry.getName().contains("module-info.class")) {
                        // Java 9 allows jars to have a module-info.class file in the root,
                        // we shouldn't complain about these.
                        continue;
                    }

                    String className = entry.getName().replaceAll("/", ".").replaceAll("\\.class$", "");
                    HashingInputStream inputStream = new HashingInputStream(Hashing.sha256(), jarInputStream);
                    ByteStreams.exhaust(inputStream);

                    hashesByClassName.put(className, inputStream.hash());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new Result(hashesByClassName.build());
        });
    }

    @Override
    public final void close() {
        // Try to free up memory when this is no longer needed
        cache.invalidateAll();
        cache.cleanUp();
    }
}
