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

package com.palantir.baseline.plugins.versions;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.options.Option;

public class CheckVersionsPropsTask extends DefaultTask {
    private Property<Boolean> fix = getProject().getObjects().property(Boolean.class);

    @Option(option = "fix", description = "Whether to apply the suggested fix to versions.props")
    public final void setFix(boolean fix) {
        this.fix.set(fix);
    }

    final Provider<Boolean> getFix() {
        return fix;
    }
}
