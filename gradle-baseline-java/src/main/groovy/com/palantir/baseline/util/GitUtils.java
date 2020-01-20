/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.util;

import java.io.File;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.util.GFileUtils;

public final class GitUtils {
    private static final Pattern GIT_ORIGIN = Pattern.compile("url = git@([^:]+):([^.]+).git");

    public static Optional<String> maybeGitHubUri() {
        try {
            String gitConfigContents = GFileUtils.readFile(new File(".git/config"));
            Matcher matcher = GIT_ORIGIN.matcher(gitConfigContents);
            if (!matcher.find()) {
                return Optional.of(String.format("https://%s/%s", matcher.group(1), matcher.group(2)));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private GitUtils() {}
}
