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
package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public final class UnsafeXmlMapperConstructionTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(UnsafeXmlMapperConstruction.class, getClass());

    @Test
    public void fails_on_new_XmlMapper_default_constructor() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                void foo() {
                    // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                    XmlMapper mapper = new XmlMapper();
                }
            }
            """);
    }

    @Test
    public void passes_on_new_XmlMapper_with_input_factory_direct() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                void foo() {
                    XmlMapper mapper = new XmlMapper(new WstxInputFactory());
                }
            }
            """);
    }

    @Test
    public void passes_on_new_XmlMapper_with_input_and_output_factory_direct() {
        test(
                """
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                void foo() {
                    XmlMapper mapper = new XmlMapper(new WstxInputFactory(), new WstxOutputFactory());
                }
            }
            """);
    }

    @Test
    public void passes_on_new_XmlMapper_with_input_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlFactory factory = new XmlFactory(new WstxInputFactory());
                    XmlMapper mapper = new XmlMapper(factory);
                }
            }
            """);
    }

    @Test
    public void passes_on_new_XmlMapper_with_input_and_output_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlFactory factory = new XmlFactory(new WstxInputFactory(), new WstxOutputFactory());
                    XmlMapper mapper = new XmlMapper(factory);
                }
            }
            """);
    }

    @Test
    public void passes_on_new_XmlMapper_with_inline_input_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlMapper mapper = new XmlMapper(new XmlFactory(new WstxInputFactory()));
                }
            }
            """);
    }

    @Test
    public void passes_on_new_XmlMapper_with_inline_input_and_output_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlMapper mapper = new XmlMapper(new XmlFactory(new WstxInputFactory(), new WstxOutputFactory()));
                }
            }
            """);
    }

    @Test
    public void fails_on_new_XmlMapper_with_unsafe_XmlFactory_no_args() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlFactory factory = new XmlFactory();
                    // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                    XmlMapper mapper = new XmlMapper(factory);
                }
            }
            """);
    }

    @Test
    public void fails_on_new_XmlMapper_with_inline_unsafe_XmlFactory_no_args() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                    XmlMapper mapper = new XmlMapper(new XmlFactory());
                }
            }
            """);
    }

    @Test
    public void fails_on_static_field_with_XmlMapper_default_constructor() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                private static final XmlMapper xmlMapper = new XmlMapper();
            }
            """);
    }

    @Test
    public void passes_on_static_field_with_input_factory_direct() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                private static final XmlMapper xmlMapper = new XmlMapper(new WstxInputFactory());
            }
            """);
    }

    @Test
    public void passes_on_static_field_with_input_and_output_factory_direct() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                private static final XmlMapper xmlMapper = new XmlMapper(new WstxInputFactory(),
                                                                                            new WstxOutputFactory());
            }
            """);
    }

    @Test
    public void passes_on_static_field_with_input_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                private static final XmlFactory factory = new XmlFactory(new WstxInputFactory());
                private static final XmlMapper xmlMapper = new XmlMapper(factory);
            }
            """);
    }

    @Test
    public void passes_on_static_field_with_inline_input_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                private static final XmlMapper xmlMapper = new XmlMapper(new XmlFactory(new WstxInputFactory()));
            }
            """);
    }

    @Test
    public void passes_on_static_field_with_input_and_output_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                private static final XmlFactory factory = new XmlFactory(new WstxInputFactory(),
                                                                                            new WstxOutputFactory());
                private static final XmlMapper xmlMapper = new XmlMapper(factory);
            }
            """);
    }

    @Test
    public void passes_on_static_field_with_inline_input_and_output_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                private static final XmlMapper xmlMapper = new XmlMapper(new XmlFactory(new WstxInputFactory(),
                                                                                            new WstxOutputFactory()));
            }
            """);
    }

    @Test
    public void fails_on_static_field_with_unsafe_XmlFactory_no_args() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                private static final XmlFactory factory = new XmlFactory();
                // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                private static final XmlMapper xmlMapper = new XmlMapper(factory);
            }
            """);
    }

    @Test
    public void fails_on_XmlMapper_builder_without_factory() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                void foo() {
                    // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                    XmlMapper mapper = XmlMapper.builder().build();
                }
            }
            """);
    }

    @Test
    public void passes_on_XmlMapper_builder_with_input_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlFactory factory = new XmlFactory(new WstxInputFactory());
                    XmlMapper mapper = XmlMapper.builder(factory).build();
                }
            }
            """);
    }

    @Test
    public void passes_on_XmlMapper_builder_with_output_factory_via_XmlFactory() {
        test(
                """
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlFactory factory = new XmlFactory(new WstxInputFactory(), new WstxOutputFactory());
                    XmlMapper mapper = XmlMapper.builder(factory).build();
                }
            }
            """);
    }

    @Test
    public void passes_on_XmlMapper_builder_with_input_factory_direct() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                void foo() {
                    XmlMapper mapper =
                XmlMapper.builder(new XmlFactory(new WstxInputFactory())).build();
                }
            }
            """);
    }

    @Test
    public void passes_on_XmlMapper_builder_with_input_and_output_factory_direct() {
        test(
                """
            import com.ctc.wstx.stax.WstxInputFactory;
            import com.ctc.wstx.stax.WstxOutputFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            class Test {
                void foo() {
                    XmlMapper mapper = XmlMapper.builder(new XmlFactory(new WstxInputFactory(),
                                                                                    new WstxOutputFactory())).build();
                }
            }
            """);
    }

    @Test
    public void fails_on_XmlMapper_builder_with_unsafe_XmlFactory_no_args() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    XmlFactory factory = new XmlFactory();
                    // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                    XmlMapper mapper = XmlMapper.builder(factory).build();
                }
            }
            """);
    }

    @Test
    public void fails_on_XmlMapper_builder_with_inline_unsafe_XmlFactory_no_args() {
        test(
                """
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;
            class Test {
                void foo() {
                    // BUG: Diagnostic contains: constructed with XMLInput/XMLOutput factories
                    XmlMapper mapper = XmlMapper.builder(new XmlFactory()).build();
                }
            }
            """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }

    @Nested
    class Refactoring {
        private RefactoringValidator bestEffortRefactoringValidator() {
            return refactoringValidator("-XepOpt:GradleGuide:BestEffortMode");
        }

        private RefactoringValidator refactoringValidator(String... args) {
            return RefactoringValidator.of(UnsafeXmlMapperConstruction.class, getClass(), args);
        }

        @Test
        void replaces_new_XmlMapper_default_constructor() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {
                            XmlMapper mapper = new XmlMapper();
                        }
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {
                            XmlMapper mapper = new XmlMapper(new WstxInputFactory(), new WstxOutputFactory());
                        }
                    }
                    """);
        }

        @Test
        void replaces_new_XmlMapper_with_unsafe_XmlFactory_no_args() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    class Test {
                        void foo() {
                            XmlFactory factory = new XmlFactory();
                            XmlMapper mapper = new XmlMapper(factory);
                        }
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {

                            XmlMapper mapper = new XmlMapper(new WstxInputFactory(), new WstxOutputFactory());
                        }
                    }
                    """);
        }

        @Test
        void replaces_new_XmlMapper_with_inline_unsafe_XmlFactory_no_args() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    class Test {
                        void foo() {
                            XmlMapper mapper = new XmlMapper(new XmlFactory());
                        }
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {
                            XmlMapper mapper = new XmlMapper(new WstxInputFactory(), new WstxOutputFactory());
                        }
                    }
                    """);
        }

        @Test
        void replaces_static_field_with_XmlMapper_default_constructor() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        private static final XmlMapper xmlMapper = new XmlMapper();
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        private static final XmlMapper xmlMapper = new XmlMapper(new WstxInputFactory(),
                                                                                            new WstxOutputFactory());
                    }
                    """);
        }

        @Test
        void replaces_static_field_with_unsafe_XmlFactory_no_args() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    class Test {
                        private static final XmlFactory factory = new XmlFactory();
                        private static final XmlMapper xmlMapper = new XmlMapper(factory);
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {

                        private static final XmlMapper xmlMapper = new XmlMapper(new WstxInputFactory(),
                                                                                            new WstxOutputFactory());
                    }
                    """);
        }

        @Test
        void replaces_XmlMapper_builder_without_factory() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {
                            XmlMapper mapper = XmlMapper.builder().build();
                        }
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {
                            XmlMapper mapper = XmlMapper.builder(new XmlFactory(new WstxInputFactory(),
                                                                                    new WstxOutputFactory())).build();
                        }
                    }
                    """);
        }

        @Test
        void replaces_XmlMapper_builder_with_unsafe_XmlFactory_no_args() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    class Test {
                        void foo() {
                            XmlFactory factory = new XmlFactory();
                            XmlMapper mapper = XmlMapper.builder(factory).build();
                        }
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {

                            XmlMapper mapper = XmlMapper.builder(new XmlFactory(new WstxInputFactory(),
                                                                                    new WstxOutputFactory())).build();
                        }
                    }
                    """);
        }

        @Test
        void replaces_XmlMapper_builder_with_inline_unsafe_XmlFactory_no_args() {
            refactoringTest(
                    """
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    class Test {
                        void foo() {
                            XmlMapper mapper = XmlMapper.builder(new XmlFactory()).build();
                        }
                    }
                    """,
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.ctc.wstx.stax.WstxOutputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {
                            XmlMapper mapper = XmlMapper.builder(new XmlFactory(new WstxInputFactory(),
                                                                                    new WstxOutputFactory())).build();
                        }
                    }
                    """);
        }

        @Test
        void does_not_change_safe_XmlMapper_with_input_factory() {
            refactoringNoop(
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    class Test {
                        void foo() {
                            XmlMapper mapper = new XmlMapper(new WstxInputFactory());
                        }
                    }
                    """);
        }

        @Test
        void does_not_change_safe_XmlMapper_with_input_factory_via_XmlFactory() {
            refactoringNoop(
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    class Test {
                        void foo() {
                            XmlFactory factory = new XmlFactory(new WstxInputFactory());
                            XmlMapper mapper = new XmlMapper(factory);
                        }
                    }
                    """);
        }

        @Test
        void does_not_change_safe_XmlMapper_builder_with_input_factory() {
            refactoringNoop(
                    """
                    import com.ctc.wstx.stax.WstxInputFactory;
                    import com.fasterxml.jackson.dataformat.xml.XmlMapper;
                    import com.fasterxml.jackson.dataformat.xml.XmlFactory;
                    class Test {
                        void foo() {
                            XmlFactory factory = new XmlFactory(new WstxInputFactory());
                            XmlMapper mapper = XmlMapper.builder(factory).build();
                        }
                    }
                    """);
        }

        private void refactoringTest(@Language("Java") String input, @Language("Java") String expected) {
            bestEffortRefactoringValidator()
                    .addInputLines("Test.java", input)
                    .addOutputLines("Test.java", expected)
                    .doTest();
        }

        private void refactoringNoop(@Language("Java") String code) {
            bestEffortRefactoringValidator()
                    .addInputLines("Test.java", code)
                    .expectUnchanged()
                    .doTest();
        }
    }
}
