package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class DangerousStringInternUsageTest {

    private CompilationTestHelper compilationHelper;

    @Before
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(DangerousStringInternUsage.class, getClass());
    }

    @Test
    public void should_warn_when_parallel_with_no_arguments_is_invoked_on_subclass_of_java_stream() {
        compilationHelper.addSourceLines(
                "Test.java",
                "class Test {",
                "   String f() {",
                "       // BUG: Diagnostic contains: Should not use String.intern().",
                "       return getClass().getName().intern();",
                "   }",
                "}"
        ).doTest();
    }
}