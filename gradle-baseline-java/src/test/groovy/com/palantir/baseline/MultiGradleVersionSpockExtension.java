/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline;

import groovy.lang.GroovyObject;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.spockframework.runtime.extension.IAnnotationDrivenExtension;
import org.spockframework.runtime.model.DataProviderInfo;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.Invoker;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.MethodKind;
import org.spockframework.runtime.model.SpecInfo;

public final class MultiGradleVersionSpockExtension implements IAnnotationDrivenExtension<MultiGradleVersions> {
    private static final String GRADLE_VERSION = "gradleVersionNumber";

    @Override
    public void visitSpecAnnotation(MultiGradleVersions annotation, SpecInfo spec) {
        spec.getAllFeatures().forEach(feature -> {
            addInterceptor(feature);
            addDataSource(feature);
        });
    }

    private void addInterceptor(FeatureInfo feature) {
        feature.addIterationInterceptor(invocation -> {
            IterationInfo iteration = invocation.getIteration();

            Object gradleVersion = Optional.ofNullable(
                            iteration.getDataVariables().get(GRADLE_VERSION))
                    .orElseThrow();
            Object testInstance = invocation.getInstance();
            setGradleVersion(testInstance, gradleVersion);

            Object[] originalDataValuesArray = iteration.getDataValues();

            Field dataValues = IterationInfo.class.getDeclaredField("dataValues");
            try {
                dataValues.setAccessible(true);

                dataValues.set(
                        iteration,
                        Arrays.stream(originalDataValuesArray)
                                .limit(originalDataValuesArray.length - 1)
                                .toArray());

                invocation.proceed();
            } finally {
                dataValues.set(iteration, originalDataValuesArray);
            }
        });
    }

    private static void setGradleVersion(Object testInstance, Object gradleVersion) {
        ((GroovyObject) testInstance).setProperty("gradleVersion", gradleVersion);
    }

    private void addDataSource(FeatureInfo feature) {
        // https://github.com/nskvortsov/gradle/blob/f98fb2a13ce7358ba2d88349d7048cd1a6f8ed4a/subprojects/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/extensions/AbstractMultiTestInterceptor.java#L236
        List<String> blah = List.of("7.6.2", "8.2.1");
        MethodInfo methodInfo = new MethodInfo((target, arguments) -> {
            return blah;
        });

        methodInfo.setKind(MethodKind.DATA_PROVIDER);
        methodInfo.setFeature(feature);
        //        try {
        //            methodInfo.setReflection(MultiGradleVersionSpockExtension.class.getDeclaredMethod("lol"));
        //        } catch (NoSuchMethodException e) {
        //            throw new RuntimeException(e);
        //        }

        DataProviderInfo dataProviderInfo = new DataProviderInfo();
        dataProviderInfo.setParent(feature);
        dataProviderInfo.setDataVariables(List.of(GRADLE_VERSION));
        dataProviderInfo.setDataProviderMethod(methodInfo);
        dataProviderInfo.setPreviousDataTableVariables(List.of());

        feature.addDataProvider(dataProviderInfo);
        feature.addDataVariable(GRADLE_VERSION);
        //        feature.addParameterName(GRADLE_VERSION);

        MethodInfo dataProcessor = new MethodInfo(new Invoker() {
            @Override
            public Object invoke(Object target, Object... arguments) throws Throwable {
                // setGradleVersion(target, "7.6.2");
                return arguments;
            }
        });
        dataProcessor.setKind(MethodKind.DATA_PROCESSOR);
        dataProcessor.setFeature(feature);

        feature.setDataProcessorMethod(dataProcessor);
    }
}
