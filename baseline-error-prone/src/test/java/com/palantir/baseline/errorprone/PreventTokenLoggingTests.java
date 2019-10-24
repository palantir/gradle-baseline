/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class PreventTokenLoggingTests {

    private CompilationTestHelper compilationHelper;

    @BeforeEach
    public void before() {
        compilationHelper = CompilationTestHelper.newInstance(PreventTokenLogging.class, getClass());
    }

    @Test
    public void testSlf4jAuthHeaderTrace() {
        failSlf4j("log.trace(message, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderTraceMultipleArgs() {
        failSlf4j("log.trace(message, arg1, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderDebug() {
        failSlf4j("log.debug(message, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderDebugMultipleArgs() {
        failSlf4j("log.debug(message, arg1, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderInfo() {
        failSlf4j("log.info(message, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderInfoMultipleArgs() {
        failSlf4j("log.info(message, arg1, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderWarn() {
        failSlf4j("log.warn(message, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderWarnMultipleArgs() {
        failSlf4j("log.warn(message, arg1, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderError() {
        failSlf4j("log.error(message, authHeader);");
    }

    @Test
    public void testSlf4jAuthHeaderErrorMultipleArgs() {
        failSlf4j("log.error(message, arg1, authHeader);");
    }

    @Test
    public void testSlf4jBearerTokenTrace() {
        failSlf4j("log.trace(message, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenTraceMultipleArgs() {
        failSlf4j("log.trace(message, arg1, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenDebug() {
        failSlf4j("log.debug(message, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenDebugMultipleArgs() {
        failSlf4j("log.debug(message, arg1, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenInfo() {
        failSlf4j("log.info(message, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenInfoMultipleArgs() {
        failSlf4j("log.info(message, arg1, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenWarn() {
        failSlf4j("log.warn(message, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenWarnMultipleArgs() {
        failSlf4j("log.warn(message, arg1, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenError() {
        failSlf4j("log.error(message, bearerToken);");
    }

    @Test
    public void testSlf4jBearerTokenErrorMultipleArgs() {
        failSlf4j("log.error(message, arg1, bearerToken);");
    }

    @Test
    public void testSlf4jTrace() {
        passSlf4j("log.trace(message, arg1);");
    }

    @Test
    public void testSlf4jTraceMultipleArgs() {
        passSlf4j("log.trace(message, arg1, arg2);");
    }

    @Test
    public void testSlf4jTraceNoArgs() {
        passSlf4j("log.trace(message);");
    }

    @Test
    public void testSlf4jTraceNullMessageNoArgs() {
        passSlf4j("log.trace(null);");
    }

    @Test
    public void testSlf4jTraceNullArg() {
        passSlf4j("log.trace(message, arg1, null);");
    }

    @Test
    public void testSlf4jDebug() {
        passSlf4j("log.debug(message, arg1);");
    }

    @Test
    public void testSlf4jDebugMultipleArgs() {
        passSlf4j("log.debug(message, arg1, arg2);");
    }

    @Test
    public void testSlf4jDebugNoArgs() {
        passSlf4j("log.debug(message);");
    }

    @Test
    public void testSlf4jDebugNullMessageNoArgs() {
        passSlf4j("log.debug(null);");
    }

    @Test
    public void testSlf4jDebugNullArg() {
        passSlf4j("log.debug(message, arg1, null);");
    }

    @Test
    public void testSlf4jInfo() {
        passSlf4j("log.info(message, arg1);");
    }

    @Test
    public void testSlf4jInfoMultipleArgs() {
        passSlf4j("log.info(message, arg1, arg2);");
    }

    @Test
    public void testSlf4jInfoNoArgs() {
        passSlf4j("log.info(message);");
    }

    @Test
    public void testSlf4jInfoNullMessageNoArgs() {
        passSlf4j("log.info(null);");
    }

    @Test
    public void testSlf4jInfoNullArg() {
        passSlf4j("log.info(message, arg1, null);");
    }

    @Test
    public void testSlf4jWarn() {
        passSlf4j("log.warn(message, arg1);");
    }

    @Test
    public void testSlf4jWarnMultipleArgs() {
        passSlf4j("log.warn(message, arg1, arg2);");
    }

    @Test
    public void testSlf4jWarnNoArgs() {
        passSlf4j("log.warn(message);");
    }

    @Test
    public void testSlf4jWarnNullMessageNoArgs() {
        passSlf4j("log.warn(null);");
    }

    @Test
    public void testSlf4jWarnNullArg() {
        passSlf4j("log.warn(message, arg1, null);");
    }

    @Test
    public void testSlf4jError() {
        passSlf4j("log.error(message, arg1);");
    }

    @Test
    public void testSlf4jErrorMultipleArgs() {
        passSlf4j("log.error(message, arg1, arg2);");
    }

    @Test
    public void testSlf4jErrorNoArgs() {
        passSlf4j("log.error(message);");
    }

    @Test
    public void testSlf4jErrorNullMessageNoArgs() {
        passSlf4j("log.error(null);");
    }

    @Test
    public void testSlf4jErrorNullArg() {
        passSlf4j("log.error(message, arg1, null);");
    }

    @Test
    public void testSafeArgAuthHeader() {
        failLogSafe("SafeArg.of(name, authHeader);");
    }

    @Test
    public void testUnsafeArgAuthHeader() {
        failLogSafe("UnsafeArg.of(name, bearerToken);");
    }

    @Test
    public void testSafeArgBearerToken() {
        failLogSafe("SafeArg.of(name, authHeader);");
    }

    @Test
    public void testUnsafeArgBearerToken() {
        failLogSafe("UnsafeArg.of(name, bearerToken);");
    }

    @Test
    public void testSafeArg() {
        passLogSafe("SafeArg.of(name, value);");
    }

    @Test
    public void testSafeArgNullName() {
        passLogSafe("SafeArg.of(null, value);");
    }

    @Test
    public void testSafeArgNullValue() {
        passLogSafe("SafeArg.of(name, null);");
    }

    @Test
    public void testUnsafeArg() {
        passLogSafe("UnsafeArg.of(name, value);");
    }

    @Test
    public void testUnsafeArgNullName() {
        passLogSafe("UnsafeArg.of(null, value);");
    }

    @Test
    public void testUnsafeArgNullValue() {
        passLogSafe("UnsafeArg.of(name, null);");
    }

    private void passSlf4j(String statement) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "import com.palantir.tokens.auth.AuthHeader;",
                        "import com.palantir.tokens.auth.BearerToken;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(AuthHeader authHeader, BearerToken bearerToken, String message, Object arg1, Object"
                                + " arg2) {",
                        "    " + statement,
                        "  }",
                        "}")
                .doTest();
    }

    private void failSlf4j(String statement) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import org.slf4j.Logger;",
                        "import org.slf4j.LoggerFactory;",
                        "import com.palantir.tokens.auth.AuthHeader;",
                        "import com.palantir.tokens.auth.BearerToken;",
                        "class Test {",
                        "  private static final Logger log = LoggerFactory.getLogger(Test.class);",
                        "  void f(AuthHeader authHeader, BearerToken bearerToken, String message, Object arg1, Object"
                                + " arg2) {",
                        "    // BUG: Diagnostic contains: not allowed to be logged",
                        "    " + statement,
                        "  }",
                        "}")
                .doTest();
    }

    private void passLogSafe(String statement) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import com.palantir.tokens.auth.AuthHeader;",
                        "import com.palantir.tokens.auth.BearerToken;",
                        "class Test {",
                        "  void f(AuthHeader authHeader, BearerToken bearerToken, String name, Object value) {",
                        "    " + statement,
                        "  }",
                        "}")
                .doTest();
    }

    private void failLogSafe(String statement) {
        compilationHelper
                .addSourceLines(
                        "Test.java",
                        "import com.palantir.logsafe.SafeArg;",
                        "import com.palantir.logsafe.UnsafeArg;",
                        "import com.palantir.tokens.auth.AuthHeader;",
                        "import com.palantir.tokens.auth.BearerToken;",
                        "class Test {",
                        "  void f(AuthHeader authHeader, BearerToken bearerToken, String name, Object value) {",
                        "    // BUG: Diagnostic contains: not allowed to be logged",
                        "    " + statement,
                        "  }",
                        "}")
                .doTest();
    }
}
