package com.palantir.baseline.refaster;

import org.junit.Test;

public class InvocationTargetExceptionCauseTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(InvocationTargetExceptionCause.class)
                .withInputLines(
                        "Test",
                        "import java.lang.reflect.InvocationTargetException;",
                        "public class Test {",
                        "  void unwrap(InvocationTargetException e) throws Throwable {",
                        "    throw e.getTargetException();",
                        "  }",
                        "}")
                .hasOutputLines(
                        "import java.lang.reflect.InvocationTargetException;",
                        "public class Test {",
                        "  void unwrap(InvocationTargetException e) throws Throwable {",
                        "    throw e.getCause();",
                        "  }",
                        "}");
    }
}