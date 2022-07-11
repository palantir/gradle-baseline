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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NonReusableAtlasdbPersisterTest {
    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(NonReusableAtlasdbPersister.class, getClass());
    }

    @Test
    public void failIfReusablePersisterAnnotation() {
        compilationHelper
                .addSourceLines(
                        "MyPersister.java",
                        "import com.palantir.atlasdb.persist.api.Persister;",
                        "import com.palantir.atlasdb.annotation.Reusable;",
                        "@Reusable",
                        "// BUG: Diagnostic contains: All AtlasDB persisters must be re-usable.",
                        "class MyPersister implements Persister<String> {",
                        "    @Override",
                        "    public final byte[] persistToBytes(String input) {",
                        "        return null;",
                        "    }",
                        "    @Override",
                        "    public final String hydrateFromBytes(byte[] input) {",
                        "        return null;",
                        "    }",
                        "    @Override",
                        "    public final Class<String> getPersistingClassType() {",
                        "        return String.class;",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void succeedIfNestedJacksonPersister() {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.atlasdb.persister.JacksonPersister;",
                        "import com.palantir.atlasdb.annotation.Reusable;",
                        "class Test {",
                        "    static class MyPersister extends JacksonPersister<String> {",
                        "        public MyPersister() {",
                        "            super(String.class, null);",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void suppliesFix() {
        RefactoringValidator.of(NonReusableAtlasdbPersister.class, getClass())
                .addInputLines(
                        "Test.java",
                        "import com.palantir.atlasdb.annotation.Reusable;",
                        "import com.palantir.atlasdb.persist.api.Persister;",
                        "class Test {",
                        "    @Reusable",
                        "    static class MyPersister implements Persister<String> {",
                        "        @Override",
                        "        public final byte[] persistToBytes(String input) {",
                        "            return null;",
                        "        }",
                        "        @Override",
                        "        public final String hydrateFromBytes(byte[] input) {",
                        "            return null;",
                        "        }",
                        "        @Override",
                        "        public final Class<String> getPersistingClassType() {",
                        "            return String.class;",
                        "        }",
                        "    }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.atlasdb.persist.api.ReusablePersister;",
                        "class Test {",
                        "    static class MyPersister implements ReusablePersister<String> {",
                        "        @Override",
                        "        public final byte[] persistToBytes(String input) {",
                        "            return null;",
                        "        }",
                        "        @Override",
                        "        public final String hydrateFromBytes(byte[] input) {",
                        "            return null;",
                        "        }",
                        "        @Override",
                        "        public final Class<String> getPersistingClassType() {",
                        "            return String.class;",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }
}
