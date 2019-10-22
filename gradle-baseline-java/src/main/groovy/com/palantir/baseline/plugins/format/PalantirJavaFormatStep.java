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

package com.palantir.baseline.plugins.format;

import com.diffplug.spotless.FormatterFunc;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.JarState;
import com.diffplug.spotless.Provisioner;
import com.google.common.collect.Iterables;
import com.palantir.javaformat.java.FormatterService;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.NotNull;

public final class PalantirJavaFormatStep {

    private PalantirJavaFormatStep() {}

    private static final String NAME = "palantir-java-format";

    /** Creates a step which formats everything - code, import order, and unused imports. */
    public static FormatterStep create(Configuration palantirJavaFormat) {
        final ConfigurationBackedProvisioner provisioner =
                new ConfigurationBackedProvisioner(palantirJavaFormat);
        return FormatterStep.createLazy(
                NAME,
                () -> new State(JarState.from(
                        palantirJavaFormat.getAllDependencies().stream().map(Object::toString).collect(
                                Collectors.toList()),
                        provisioner)),
                State::createFormat);
    }

    static final class State implements Serializable {
        private static final long serialVersionUID = 1L;

        /** Kept for state serialization purposes. */
        @SuppressWarnings("unused")
        private final String stepName = NAME;
        /** The jars that contain the palantir-java-format implementation. */
        private final JarState jarState;

        State(JarState jarState) {
            this.jarState = jarState;
        }

        FormatterFunc createFormat() throws Exception {
            ClassLoader classLoader = jarState.getClassLoader();
            FormatterService formatter =
                    Iterables.getOnlyElement(ServiceLoader.load(FormatterService.class, classLoader));
            return formatter::formatSourceReflowStringsAndFixImports;
        }
    }

    /**
     * A naughty provisioner that ignores the requested coordinates and just returns the contents of a configuration.
     */
    private static class ConfigurationBackedProvisioner implements Provisioner {
        private final Configuration configuration;

        ConfigurationBackedProvisioner(Configuration configuration) {
            this.configuration = configuration;
        }

        @NotNull
        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return configuration.getFiles();
        }
    }
}
