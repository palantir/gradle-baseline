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

package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class DangerousJavaDeserializationTest {

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(DangerousJavaDeserialization.class, getClass());
    }

    @Test
    void disallowDeserialization() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.io.ObjectInputStream;",
                        "class Test {",
                        "   Object f(ObjectInputStream ois) throws Exception {",
                        "       // BUG: Diagnostic contains: serialization features for security reasons",
                        "       return ois.readObject();",
                        "   }",
                        "}")
                .doTest();
    }

    @Test
    void allowsReadObject() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.io.*;",
                        "class Test implements Serializable {",
                        "   private synchronized void readObject(ObjectInputStream ois) throws Exception {",
                        "       ois.readObject();",
                        "   }",
                        "}")
                .doTest();
    }
}
