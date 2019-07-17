package com.palantir.baseline.refaster;

import org.junit.Test;

public class OptionalOrElseSupplierTest {

    @Test
    public void test() {
        RefasterTestHelper
                .forRefactoring(OptionalOrElseSupplier.class)
                .withInputLines(
                        "Test",
                        "import java.util.*;",
                        "import java.util.function.Supplier;",
                        "public class Test {",
                        "  Supplier<String> supplier = () -> \"hello\";",
                        "  String s = Optional.ofNullable(\"world\").orElse(supplier.get());",
                        "}")
                .hasOutputLines(
                        "import java.util.*;",
                        "import java.util.function.Supplier;",
                        "public class Test {",
                        "  Supplier<String> supplier = () -> \"hello\";",
                        "  String s = Optional.ofNullable(\"world\").orElseGet(supplier);",
                        "}");
    }

}
