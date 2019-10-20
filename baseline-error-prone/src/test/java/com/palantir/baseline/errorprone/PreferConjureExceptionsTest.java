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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class PreferConjureExceptionsTest {
    @Test
    void testNotAuthorizedException() {
        helper().addSourceLines(
                "Test.java",
                "import javax.ws.rs.NotSupportedException;",
                "class Test {",
                "   void f() {",
                "       // BUG: Diagnostic contains: Prefer throwing a ServiceException",
                "       throw new NotSupportedException();",
                "   }",
                "}"
        ).doTest();
    }

    @Test
    void testFix() {
        fix()
                .addInputLines(
                        "Test.java",
                        "import javax.ws.rs.*;",
                        "class Test {",
                        "   void f1() {",
                        "       throw new BadRequestException();",
                        "   }",
                        "   void f2(Throwable cause) {",
                        "       throw new BadRequestException(cause);",
                        "   }",
                        "   void f3() {",
                        "       throw new BadRequestException(\"message\");",
                        "   }",
                        "   void f4(String message) {",
                        "       throw new InternalServerErrorException(message);",
                        "   }",
                        "   void f5(Throwable cause) {",
                        "       throw new ForbiddenException(\"message\", cause);",
                        "   }",
                        "   void f6(String message, Throwable cause) {",
                        "       throw new NotFoundException(message, cause);",
                        "   }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.conjure.java.api.errors.ErrorType;",
                        "import com.palantir.conjure.java.api.errors.ServiceException;",
                        "import com.palantir.logsafe.exceptions.SafeRuntimeException;",
                        "import javax.ws.rs.*;",
                        "class Test {",
                        "   void f1() {",
                        "       throw new ServiceException(ErrorType.INVALID_ARGUMENT);",
                        "   }",
                        "   void f2(Throwable cause) {",
                        "       throw new ServiceException(ErrorType.INVALID_ARGUMENT, cause);",
                        "   }",
                        "   void f3() {",
                        "       throw new ServiceException(ErrorType.INVALID_ARGUMENT,",
                        "           new SafeRuntimeException(\"message\"));",
                        "   }",
                        "   void f4(String message) {",
                        "       throw new ServiceException(ErrorType.INTERNAL,",
                        "           new RuntimeException(message));",
                        "   }",
                        "   void f5(Throwable cause) {",
                        "       throw new ServiceException(ErrorType.PERMISSION_DENIED,",
                        "           new SafeRuntimeException(\"message\", cause));",
                        "   }",
                        "   void f6(String message, Throwable cause) {",
                        "       throw new ServiceException(ErrorType.NOT_FOUND,",
                        "           new RuntimeException(message, cause));",
                        "   }",
                        "}")
                .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(PreferConjureExceptions.class, getClass());
    }

    private BugCheckerRefactoringTestHelper fix() {
        return BugCheckerRefactoringTestHelper.newInstance(new PreferConjureExceptions(), getClass());
    }
}
