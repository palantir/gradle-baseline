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

package com.palantir.gradle.testing.files.arbitrary;

import com.palantir.gradle.testing.files.GradleSourceSet;
import com.palantir.gradle.testing.files.yaml.YamlFile;
import java.nio.file.Path;

public record ArbitrarySrcDir(GradleSourceSet sourceSet, String srcDirName) {
    public ArbitraryFile file(String path) {
        return new ArbitraryFile(resolvePath(path));
    }

    public YamlFile yamlFile(String path) {
        return new YamlFile(resolvePath(path));
    }

    private Path resolvePath(String path) {
        return sourceSet.path().resolve(srcDirName).resolve(path);
    }
}
