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
 */
package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;

public class ImmutablesStyleCollisionTest {

    @Test
    public void testPass() {
        helper().addSourceLines(
                        "Person.java",
                        "import org.immutables.value.Value;",
                        "public interface Person {",
                        "    String name();",
                        "}")
                .doTest();
    }

    @Test
    public void testFail() {
        helper().addSourceLines(
                        "MyMetaAnnotation.java",
                        "import org.immutables.value.Value;",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Retention;",
                        "import java.lang.annotation.RetentionPolicy;",
                        "import java.lang.annotation.Target;",
                        "import org.immutables.value.Value;",
                        "@Target({ElementType.PACKAGE, ElementType.TYPE})\n",
                        "@Retention(RetentionPolicy.CLASS)\n",
                        "@Value.Style(visibility = Value.Style.ImplementationVisibility.PUBLIC)\n",
                        "public @interface MyMetaAnnotation {}")
                .addSourceLines(
                        "Person.java",
                        "import org.immutables.value.Value;",
                        "@MyMetaAnnotation",
                        "@Value.Style(with = \"with\")",
                        "// BUG: Diagnostic contains: Immutables @Value.Style inline annotation should not be present",
                        "public interface Person {",
                        "    String name();",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesStyleCollision.class, getClass());
    }
}
