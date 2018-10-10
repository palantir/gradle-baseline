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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.inferred.freebuilder.FreeBuilder;

final class VersionsPropsReader {
    private static final Pattern VERSION_FORCE_REGEX = Pattern.compile("([^:=\\s]+:[^:=\\s]+)\\s*=\\s*([^\\s]+)");

    private VersionsPropsReader() {}

    @FreeBuilder
    public interface ParsedVersionsProps {
        List<String> lines();

        /**
         * Map of {@link VersionForce#name} to index of line in {@link #lines} that defines the force.
         */
        Map<String, Integer> namesToLocationMap();

        List<VersionForce> forces();

        VersionsPropsReader.ParsedVersionsProps.Builder toBuilder();
        class Builder extends VersionsPropsReader_ParsedVersionsProps_Builder { }
    }

    @FreeBuilder
    public interface VersionForce {
        String name();
        String version();

        VersionsPropsReader.VersionForce.Builder toBuilder();
        class Builder extends VersionsPropsReader_VersionForce_Builder { }
    }

    static ParsedVersionsProps readVersionsProps(File propsFile) {
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

    static ParsedVersionsProps readVersionsProps(Stream<String> linesStream) {
        List<String> lines = linesStream.map(String::trim).collect(Collectors.toList());

        ParsedVersionsProps.Builder builder = new ParsedVersionsProps.Builder().addAllLines(lines);
        boolean active = true;
        for (int index = 0; index < lines.size(); index++) {
            String line0 = lines.get(index);

            // skip lines while linter:OFF
            if (line0.equals("# linter:ON")) {
                active = true;
            } else if (line0.equals("# linter:OFF")) {
                active = false;
            }
            if (!active) {
                continue;
            }

            // strip comment
            int commentIndex = line0.indexOf("#");
            // trim so VERSION_FORCE_REGEX doesn't have to match leading/trailing spaces
            String line = (commentIndex >= 0 ? line0.substring(0, commentIndex) : line0).trim();
            Matcher matcher = VERSION_FORCE_REGEX.matcher(line);
            if (matcher.matches()) {
                String propName = matcher.group(1);
                String propVersion = matcher.group(2);
                VersionForce force = new VersionForce.Builder().name(propName).version(propVersion).build();
                builder.putNamesToLocationMap(force.name(), index);
                builder.addForces(force);
            }
        }
        return builder.build();
    }

}
