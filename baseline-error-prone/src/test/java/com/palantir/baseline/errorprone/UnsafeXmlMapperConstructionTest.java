package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.intellij.lang.annotations.Language;
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
                private static final XmlMapper xmlMapper = new XmlMapper(new WstxInputFactory(), new WstxOutputFactory());
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
                private static final XmlFactory factory = new XmlFactory(new WstxInputFactory(), new WstxOutputFactory());
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
                private static final XmlMapper xmlMapper = new XmlMapper(new XmlFactory(new WstxInputFactory(), new WstxOutputFactory()));
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
                    XmlMapper mapper = XmlMapper.builder(new XmlFactory(new WstxInputFactory(), new WstxOutputFactory()))
                            .build();
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
}
