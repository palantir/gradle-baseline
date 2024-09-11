/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.plugins.javaversions;

/**
 * This exists to keep the setters on the 'BaselineJavaVersionsExtension' in sync with those on
 * 'SubprojectBaselineJavaVersionsExtension'. Ideally it would have a name like 'BaselineJavaVersionsExtension'
 * with the main one called 'RootProjectBaselineJavaVersionsExtension', but that class is public API and is depended
 * on by other Gradle plugins.
 */
public interface BaselineJavaVersionsExtensionSetters {
    void setLibraryTarget(int value);

    void setLibraryTarget(String value);

    void setDistributionTarget(int value);

    void setDistributionTarget(String value);

    void setRuntime(int value);

    void setRuntime(String value);
}
