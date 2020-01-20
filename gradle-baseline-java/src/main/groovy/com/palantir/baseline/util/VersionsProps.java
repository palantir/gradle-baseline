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

package com.palantir.baseline.util;

import com.google.common.base.Preconditions;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.inferred.freebuilder.FreeBuilder;

public final class VersionsProps {
    private static final Pattern VERSION_FORCE_REGEX = Pattern.compile("([^:=\\s]+:[^:=\\s]+)\\s*=\\s*([^\\s]+)");

    private VersionsProps() {}

    @FreeBuilder
    public interface ParsedVersionsProps {
        List<String> lines();

        /** Map of {@link VersionForce#name} to index of line in {@link #lines} that defines the force. */
        Map<String, Integer> namesToLocationMap();

        List<VersionForce> forces();

        VersionsProps.ParsedVersionsProps.Builder toBuilder();

        class Builder extends VersionsProps_ParsedVersionsProps_Builder {}
    }

    @FreeBuilder
    public interface VersionForce {
        String name();

        String version();

        static VersionForce of(String name, String version) {
            return new Builder().name(name).version(version).build();
        }

        VersionsProps.VersionForce.Builder toBuilder();

        class Builder extends VersionsProps_VersionForce_Builder {}
    }

    public static ParsedVersionsProps readVersionsProps(File propsFile) {
        Preconditions.checkArgument(propsFile.exists(), "No " + propsFile.toPath() + " file found");
        try (Stream<String> lines = Files.lines(propsFile.toPath())) {
            return readVersionsProps(lines);
        } catch (IOException e) {
            throw new RuntimeException("Error reading " + propsFile.toPath() + " file", e);
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
                VersionForce force = new VersionForce.Builder()
                        .name(propName)
                        .version(propVersion)
                        .build();
                builder.putNamesToLocationMap(force.name(), index);
                builder.addForces(force);
            }
        }
        return builder.build();
    }

    /**
     * Writes back a {@link ParsedVersionsProps} to the {@code propsFile}, removing the given {@code forcesToRemove}
     * from the file.
     *
     * @throws NullPointerException if any of the {@code forcesToRemove} weren't found in
     *     {@link ParsedVersionsProps#namesToLocationMap}.
     */
    public static void writeVersionsProps(
            ParsedVersionsProps parsedVersionsProps, Stream<String> forcesToRemove, File propsFile) {
        List<String> lines = parsedVersionsProps.lines();
        Set<Integer> indicesToSkip = forcesToRemove
                .map(parsedVersionsProps.namesToLocationMap()::get)
                .map(Preconditions::checkNotNull)
                .collect(Collectors.toSet());
        try (BufferedWriter writer0 =
                        Files.newBufferedWriter(propsFile.toPath(), StandardOpenOption.TRUNCATE_EXISTING);
                PrintWriter writer = new PrintWriter(writer0)) {
            for (int index = 0; index < lines.size(); index++) {
                if (!indicesToSkip.contains(index)) {
                    writer.println(lines.get(index));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
