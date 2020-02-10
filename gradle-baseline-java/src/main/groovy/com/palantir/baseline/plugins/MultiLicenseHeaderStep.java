/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
 *
 * Original copyright:
 *
 * Copyright 2016 DiffPlug
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

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;
import com.diffplug.spotless.generic.LicenseHeaderStep;
import com.google.common.collect.Iterables;
import java.io.Serializable;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Prefixes a license header before the package statement. */
public final class MultiLicenseHeaderStep implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Note: don't change this so that we get the filtering out of bad files from
     * {@link LicenseHeaderStep#unsupportedJvmFilesFilter()} that {@link com.diffplug.gradle.spotless.JavaExtension} /
     * {@link com.diffplug.gradle.spotless.GroovyExtension} sets up for us.
     */
    private static final String NAME = "licenseHeader";

    private static final String DEFAULT_YEAR_DELIMITER = "-";

    private final Pattern delimiterPattern;

    private final List<LicenseHeader> licenseHeaders;

    /** Creates a FormatterStep which forces the start of each file to match a license header. */
    public static FormatterStep createFromHeaders(List<String> licenseHeaders, String delimiter) {
        return createFromHeaders(licenseHeaders, delimiter, DEFAULT_YEAR_DELIMITER);
    }

    private static FormatterStep createFromHeaders(
            List<String> licenseHeaders, String delimiter, String yearSeparator) {
        Objects.requireNonNull(licenseHeaders, "licenseHeader");
        Objects.requireNonNull(delimiter, "delimiter");
        Objects.requireNonNull(yearSeparator, "yearSeparator");
        return FormatterStep.create(
                MultiLicenseHeaderStep.NAME,
                new MultiLicenseHeaderStep(licenseHeaders, delimiter, yearSeparator),
                step -> step::format);
    }

    public static String name() {
        return NAME;
    }

    /** The license that we'd like enforced. */
    private MultiLicenseHeaderStep(List<String> licenseHeaders, String delimiter, String yearSeparator) {
        this.delimiterPattern = Pattern.compile('^' + delimiter, Pattern.UNIX_LINES | Pattern.MULTILINE);
        this.licenseHeaders = licenseHeaders.stream()
                .map(header -> new LicenseHeader(header, delimiter, yearSeparator))
                .collect(Collectors.toList());
    }

    private static class LicenseHeader implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String licenseHeader;
        private Pattern yearMatcherPattern;
        private final boolean hasYearToken;
        private String licenseHeaderBeforeYearToken;
        private String licenseHeaderAfterYearToken;
        private String licenseHeaderWithYearTokenReplaced;

        LicenseHeader(String licenseHeader0, String delimiter, String yearSeparator) {
            if (delimiter.contains("\n")) {
                throw new IllegalArgumentException("The delimiter must not contain any newlines.");
            }
            // sanitize the input license
            String licenseHeader1 = LineEnding.toUnix(licenseHeader0);
            if (!licenseHeader1.endsWith("\n")) {
                licenseHeader1 = licenseHeader1 + "\n";
            }
            this.licenseHeader = licenseHeader1;
            this.hasYearToken = licenseHeader1.contains("$YEAR");
            if (this.hasYearToken) {
                int yearTokenIndex = licenseHeader1.indexOf("$YEAR");
                this.licenseHeaderBeforeYearToken = licenseHeader1.substring(0, yearTokenIndex);
                this.licenseHeaderAfterYearToken = licenseHeader1.substring(yearTokenIndex + 5);
                this.licenseHeaderWithYearTokenReplaced = licenseHeader1.replace(
                        "$YEAR", String.valueOf(YearMonth.now().getYear()));
                this.yearMatcherPattern = Pattern.compile("[0-9]{4}(" + Pattern.quote(yearSeparator) + "[0-9]{4})?");
            }
        }

        private boolean matchesLicenseWithYearToken(String existingHeader) {
            int startOfTheSecondPart = existingHeader.indexOf(licenseHeaderAfterYearToken);
            return startOfTheSecondPart > licenseHeaderBeforeYearToken.length()
                    && (existingHeader.startsWith(licenseHeaderBeforeYearToken)
                            && startOfTheSecondPart + licenseHeaderAfterYearToken.length() == existingHeader.length())
                    && yearMatcherPattern
                            .matcher(existingHeader.substring(
                                    licenseHeaderBeforeYearToken.length(), startOfTheSecondPart))
                            .matches();
        }

        private boolean matches(String existingHeader) {
            if (hasYearToken) {
                // that means we have the license like `licenseHeaderBeforeYearToken 1990-2015
                // licenseHeaderAfterYearToken`
                return matchesLicenseWithYearToken(existingHeader);
            }
            return existingHeader.equals(licenseHeader);
        }

        private String render() {
            if (hasYearToken) {
                return licenseHeaderWithYearTokenReplaced;
            }
            return licenseHeader;
        }
    }

    /** Formats the given string. */
    public String format(String raw) {
        Matcher matcher = delimiterPattern.matcher(raw);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to find delimiter regex " + delimiterPattern);
        } else {
            Optional<LicenseHeader> matchingHeader = licenseHeaders.stream()
                    .filter(header -> header.matches(raw.substring(0, matcher.start())))
                    .findFirst();
            if (matchingHeader.isPresent()) {
                return raw;
            }
            // Otherwise, replace with the last header.
            return Iterables.getLast(licenseHeaders).render() + raw.substring(matcher.start());
        }
    }
}
