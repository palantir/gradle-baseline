/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.baseline.classuniqueness;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

/** Writes the {@link ClassIndex} of a jar. */
@DisableCachingByDefault(because = """
    Indexing a jar is faster than fetching its index from a remote build cache, and the local transforms \
    cache already shares indexes between projects and builds\
    """)
public abstract class ClassIndexTransform implements TransformAction<TransformParameters.None> {
    /** The artifact type of class indexes. */
    public static final String ARTIFACT_TYPE = "class-uniqueness-index";

    /** The artifact type the transform takes, which {@link IndexableArtifactTypeRule} matches with any other. */
    public static final String INPUT_ARTIFACT_TYPE = "class-uniqueness-input";

    private static final String JAR_EXTENSION = ".jar";

    /** Appended to the jar's file name, which the check needs to recover the jar's classifier. */
    private static final String EXTENSION = ".cui";

    private static final Logger log = Logging.getLogger(ClassIndexTransform.class);

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public final void transform(TransformOutputs outputs) {
        File jar = getInputArtifact().get().getAsFile();
        if (!jar.exists()) {
            // For example, the jar of a project whose jar task is disabled. Without an output, the jar is skipped.
            log.info("Skipping non-existent jar {}", jar);
            return;
        }
        if (!jar.getName().endsWith(JAR_EXTENSION)) {
            // The transform takes artifacts of every type, such as aars, zips and native libraries
            log.info("Skipping {}, which isn't a jar", jar);
            return;
        }
        Set<String> duplicates;
        try {
            duplicates = ClassIndex.write(
                    jar.toPath(), outputs.file(jar.getName() + EXTENSION).toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to index the classes of " + jar, e);
        }
        if (!duplicates.isEmpty()) {
            log.warn("""
                Warning: Gradle Baseline found a dependency jar that contains more than one zip entry for \
                a class and is likely malformed: {}
                The following entries appear multiple times: {}
                This issue should be reported to the maintainer of the dependency.\
                """, jar.getName(), duplicates);
        }
    }

    /** The name of the jar the given index was written for. */
    static String jarFileName(File index) {
        String indexName = index.getName();
        return indexName.substring(0, indexName.length() - EXTENSION.length());
    }
}
