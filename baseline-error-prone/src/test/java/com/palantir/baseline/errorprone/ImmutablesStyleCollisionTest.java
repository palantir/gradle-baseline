package com.palantir.baseline.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;

public class ImmutablesStyleCollisionTest {

    @Test
    public void testPass() {
        String sourceCode = ""
                + "import org.immutables.value.Value;"
                + "@Value.Style(with = \"with\")"
                + "public interface Person {"
                + "    String name();"
                + "}";
        helper().addSourceLines("Pass.java", sourceCode).doTest();
    }

    @Test
    public void testFail() {
        //
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(ImmutablesStyleCollision.class, getClass());
    }
}
