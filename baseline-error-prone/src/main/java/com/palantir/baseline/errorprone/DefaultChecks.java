/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.errorprone;

import com.google.errorprone.BugCheckerInfo;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.bugpatterns.BugChecker;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class DefaultChecks {
    private static List<BugCheckerInfo> enabledErrors = getPluginsWithErrorSeverity();

    private DefaultChecks() {
        throw new IllegalStateException("Utility class");
    }

    static List<BugCheckerInfo> getEnabledErrors() {
        return enabledErrors;
    }

    static List<BugCheckerInfo> getPluginsWithErrorSeverity() {
        List<BugCheckerInfo> pluginsWithErrorSeverity = new ArrayList<BugCheckerInfo>();
        ServiceLoader<BugChecker> loader = ServiceLoader.load(BugChecker.class);

        for (BugChecker bc : loader) {
            if (bc.getClass().getPackage().getName().startsWith("com.palantir.baseline.errorprone")) {
                BugPattern bugPattern = bc.getClass().getAnnotation(BugPattern.class);
                if (bugPattern.severity() == SeverityLevel.ERROR) {
                    BugCheckerInfo bcInfo = BugCheckerInfo.create(bc.getClass());
                    pluginsWithErrorSeverity.add(bcInfo);
                }
            }
        }
        return pluginsWithErrorSeverity;
    }
}
