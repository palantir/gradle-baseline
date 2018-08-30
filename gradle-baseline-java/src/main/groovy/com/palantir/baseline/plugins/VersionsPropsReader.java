/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

final class VersionsPropsReader {
    private static final Pattern VERSION_FORCE_REGEX = Pattern.compile("([^:=\\s]+:[^:=\\s]+)\\s*=\\s*([^\\s]+)");

    private VersionsPropsReader() {}

    static List<Pair<String, String>> readVersionsProps(File propsFile) {
        if (propsFile.exists()) {
            try (Stream<String> lines = Files.lines(propsFile.toPath())) {
                return readVersionsProps(lines);
            } catch (IOException e) {
                throw new RuntimeException("Error reading " + propsFile.toPath() + " file", e);
            }
        } else {
            throw new RuntimeException("No " + propsFile.toPath() + " file found");
        }
    }

    static List<Pair<String, String>> readVersionsProps(Stream<String> lines) {
        ImmutableList.Builder<Pair<String, String>> accumulator = ImmutableList.builder();
        new ToggleableIterator(lines.map(String::trim).iterator()).forEachRemaining(line0 -> {
            // strip comment
            int commentIndex = line0.indexOf("#");
            // trim so VERSION_FORCE_REGEX doesn't have to match leading/trailing spaces
            String line = (commentIndex >= 0 ? line0.substring(0, commentIndex) : line0).trim();
            Matcher matcher = VERSION_FORCE_REGEX.matcher(line);
            if (matcher.matches()) {
                String propName = matcher.group(1);
                String propVersion = matcher.group(2);
                accumulator.add(Pair.of(propName, propVersion));
            }
        });
        return accumulator.build();
    }

    private static class ToggleableIterator extends AbstractIterator<String> {
        private final Iterator<String> iter;
        private boolean active;

        ToggleableIterator(Iterator<String> iter) {
            this.iter = iter;
            active = true;
        }

        @Override
        protected String computeNext() {
            while (iter.hasNext()) {
                String line0 = iter.next();
                if (line0.equals("# linter:ON")) {
                    active = true;
                } else if (line0.equals("# linter:OFF")) {
                    active = false;
                }
                if (!active) {
                    continue;
                }
                return line0;
            }
            return endOfData();
        }
    }
}
