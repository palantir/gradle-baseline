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

import com.palantir.baseline.classuniqueness.ConflictFinder.Jar;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * Warns about jars that contain a class more than once, once per jar per build, however many projects check the jar.
 * Such a jar is likely malformed, and only its maintainer can fix it.
 */
public abstract class DuplicateEntryWarnings implements BuildService<BuildServiceParameters.None> {
    /** The name the plugin registers this service under. */
    public static final String NAME = "classUniquenessDuplicateEntryWarnings";

    private static final Logger log = Logging.getLogger(DuplicateEntryWarnings.class);

    /** The indexes of the jars checked so far, which are unique to each jar's content. */
    private final Set<Path> checkedIndexes = ConcurrentHashMap.newKeySet();

    /** Warns if the jar contains a class more than once, unless this build has already checked the same jar. */
    public final void warnIfMalformed(Jar jar) throws IOException {
        if (!checkedIndexes.add(jar.index().file())) {
            return;
        }
        SortedSet<String> duplicatedNames = jar.index().duplicatedNames();
        if (!duplicatedNames.isEmpty()) {
            log.warn("""
                Warning: Gradle Baseline found a dependency jar that contains more than one zip entry for \
                a class and is likely malformed: {}
                The following entries appear multiple times: {}
                This issue should be reported to the maintainer of the dependency.\
                """, ClassIndexTransform.jarFileName(jar.index().file().toFile()), duplicatedNames);
        }
    }
}
