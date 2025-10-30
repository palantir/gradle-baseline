/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.modulejvmargs.compilerplugins;

import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.util.Context;
import java.lang.reflect.Field;

/**
 * The name is intentionally long to instil the appropriate level of fear.
 */
@AutoService(Plugin.class)
public final class AllowReleaseAndAddExportsToBeUsedTogetherByChangingCompilerInternalsUsingReflection
        implements Plugin {

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public void init(JavacTask task, String... _args) {
        Context context = ((BasicJavacTask) task).getContext();
        Modules modules = Modules.instance(context);

        try {
            // As of writing, this field has not been changed in 8 years:
            // https://github.com/openjdk/jdk/blame/a33aa65fbc70a91fe21e9016c393bb5a764cd75a/
            //      src/jdk.compiler/share/classes/com/sun/tools/javac/comp/Modules.java#L150
            // We can but pray that it does not change in the future.
            Field allowAccessIntoSystem = Modules.class.getDeclaredField("allowAccessIntoSystem");
            allowAccessIntoSystem.setAccessible(true);
            allowAccessIntoSystem.setBoolean(modules, true);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
