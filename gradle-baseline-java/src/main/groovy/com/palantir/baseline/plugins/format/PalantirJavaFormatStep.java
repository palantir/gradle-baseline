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
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.NotNull;

public final class PalantirJavaFormatStep {

    private PalantirJavaFormatStep() {}

    private static final String NAME = "palantir-java-format";

    private static final String FORMATTER_CLASS = "com.palantir.javaformat.java.Formatter";
    private static final String FORMATTER_CREATE_METHOD = "createFormatter";

    private static final String FORMATTER_METHOD = "formatSourceAndFixImports";

    private static final String OPTIONS_CLASS = "com.palantir.javaformat.java.JavaFormatterOptions";
    private static final String OPTIONS_BUILDER_METHOD = "builder";
    private static final String OPTIONS_BUILDER_CLASS = "com.palantir.javaformat.java.JavaFormatterOptions$Builder";
    private static final String OPTIONS_BUILDER_STYLE_METHOD = "style";
    private static final String OPTIONS_BUILDER_BUILD_METHOD = "build";
    private static final String OPTIONS_Style = "com.palantir.javaformat.java.JavaFormatterOptions$Style";

    /** Creates a step which formats everything - code, import order, and unused imports. */
    public static FormatterStep create(Configuration palantirJavaFormat) {

        return FormatterStep.createLazy(
                NAME,
                () -> new State(
                        palantirJavaFormat.getAllDependencies().stream().map(Object::toString).collect(
                                Collectors.toList()),
                        new ConfigurationBackedProvisioner(palantirJavaFormat)),
                State::createFormat);
    }

    static final class State implements Serializable {
        private static final long serialVersionUID = 1L;

        /** Kept for state serialization purposes. */
        @SuppressWarnings("unused")
        private final String stepName = NAME;
        /** The jar that contains the palantir-java-format implementation. */
        private final JarState jarState;

        State(Collection<String> deps, Provisioner provisioner) throws IOException {
            this.jarState = JarState.from(deps, provisioner);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        FormatterFunc createFormat() throws Exception {
            ClassLoader classLoader = jarState.getClassLoader();

            Class<?> optionsClass = classLoader.loadClass(OPTIONS_CLASS);
            Class<?> optionsBuilderClass = classLoader.loadClass(OPTIONS_BUILDER_CLASS);
            Method optionsBuilderMethod = optionsClass.getMethod(OPTIONS_BUILDER_METHOD);
            Object optionsBuilder = optionsBuilderMethod.invoke(null);

            Class<?> optionsStyleClass = classLoader.loadClass(OPTIONS_Style);
            Object styleConstant = Enum.valueOf((Class<Enum>) optionsStyleClass, "PALANTIR");
            Method optionsBuilderStyleMethod =
                    optionsBuilderClass.getMethod(OPTIONS_BUILDER_STYLE_METHOD, optionsStyleClass);
            optionsBuilderStyleMethod.invoke(optionsBuilder, styleConstant);

            Method optionsBuilderBuildMethod = optionsBuilderClass.getMethod(OPTIONS_BUILDER_BUILD_METHOD);
            Object options = optionsBuilderBuildMethod.invoke(optionsBuilder);

            // instantiate the formatter and get its format method
            Class<?> formatterClazz = classLoader.loadClass(FORMATTER_CLASS);
            Object formatter = formatterClazz.getMethod(FORMATTER_CREATE_METHOD, optionsClass).invoke(null, options);
            Method formatterMethod = formatterClazz.getMethod(FORMATTER_METHOD, String.class);

            return input -> (String) formatterMethod.invoke(formatter, input);
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
