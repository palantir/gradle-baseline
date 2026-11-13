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

import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;

/**
 * Lets {@link ClassIndexTransform} take artifacts of every type, by matching its input type with any other.
 *
 * <p>A jar's artifact type can be anything, since Gradle takes it from the Maven POM: a dependency's {@code type},
 * such as {@code test-jar}, or a module's {@code packaging}, such as {@code takari-maven-component}. The transform
 * skips any artifact that isn't a jar.
 */
public final class IndexableArtifactTypeRule implements AttributeCompatibilityRule<String> {
    @Override
    public void execute(CompatibilityCheckDetails<String> details) {
        // Not the transform's own output, so Gradle never chains the transform onto itself
        if (ClassIndexTransform.INPUT_ARTIFACT_TYPE.equals(details.getConsumerValue())
                && !ClassIndexTransform.ARTIFACT_TYPE.equals(details.getProducerValue())) {
            details.compatible();
        }
    }
}
