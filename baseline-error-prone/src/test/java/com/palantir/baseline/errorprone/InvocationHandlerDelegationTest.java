/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class InvocationHandlerDelegationTest {

    @Test
    void testSimpleInvocationHandler() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.InvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "final class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        "    // BUG: Diagnostic contains: unwrap InvocationTargetException",
                        "    return method.invoke(this, args);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testSimpleAbstractInvocationHandler() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.common.reflect.AbstractInvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "final class Test extends AbstractInvocationHandler {",
                        "  @Override",
                        "  public Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable"
                                + " {",
                        "    // BUG: Diagnostic contains: unwrap InvocationTargetException",
                        "    return method.invoke(this, args);",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAllowedWithoutDynamicInvocation() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.common.reflect.AbstractInvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "final class Test extends AbstractInvocationHandler {",
                        "  @Override",
                        "  public Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable"
                                + " {",
                        "    return this.toString();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testCorrectAbstractInvocationHandler_getCause() {
        helper().addSourceLines(
                        "Test.java",
                        "import com.google.common.reflect.AbstractInvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "import java.lang.reflect.InvocationTargetException;",
                        "final class Test extends AbstractInvocationHandler {",
                        "  @Override",
                        "  public Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable"
                                + " {",
                        "    try {",
                        "      return method.invoke(this, args);",
                        "    } catch (InvocationTargetException e) {",
                        "        throw e.getCause();",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testCorrectInvocationHandler_getTargetException() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.InvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "import java.lang.reflect.InvocationTargetException;",
                        "final class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        "    try {",
                        "      return method.invoke(this, args);",
                        "    } catch (InvocationTargetException e) {",
                        "        throw e.getTargetException();",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testCorrectInvocationHandler_doesntRethrow() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.InvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "import java.lang.reflect.InvocationTargetException;",
                        "final class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        "    try {",
                        "      return method.invoke(this, args);",
                        "    } catch (InvocationTargetException e) {",
                        // Some proxies may check and rewrap the cause, the
                        // important part is that it's handled in some way.
                        "        return e.getCause();",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testCorrectInvocationHandler_lambda() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.*;",
                        "import com.google.common.util.concurrent.*;",
                        "abstract class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        // Avoid flagging invocations nested in anonymous classes or lambdas
                        "  return FluentFuture.from(executor().submit(() -> method.invoke(this, args)))",
                        "    .catching(InvocationTargetException.class, ignored -> null,"
                                + " MoreExecutors.directExecutor())",
                        "    .get();",
                        "  }",
                        "  abstract ListeningExecutorService executor();",
                        "}")
                .doTest();
    }

    @Test
    void testCorrectInvocationHandler_delegatesException() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.InvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "import java.lang.reflect.InvocationTargetException;",
                        "final class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        "    try {",
                        "      return method.invoke(this, args);",
                        "    } catch (InvocationTargetException e) {",
                        "        throw handleInvocationTargetException(e);",
                        "    }",
                        "  }",
                        "  private Throwable handleInvocationTargetException(InvocationTargetException e) throws"
                                + " Throwable {",
                        "    throw e.getCause();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testInvocationHandler_instanceOf_if() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.InvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "import java.lang.reflect.InvocationTargetException;",
                        "final class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        "    try {",
                        "      return method.invoke(this, args);",
                        "    } catch (Throwable t) {",
                        "      if (t instanceof InvocationTargetException) {",
                        "        throw t.getCause();",
                        "      }",
                        "      throw t;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testInvocationHandler_instanceOf_ternary() {
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.InvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "import java.lang.reflect.InvocationTargetException;",
                        "final class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        "    try {",
                        "      return method.invoke(this, args);",
                        "    } catch (Throwable t) {",
                        "      throw (t instanceof InvocationTargetException) ? t.getCause() : t;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testInvocationHandler_delegatedThrowable() {
        // This implementation is functionally correct, but risks breaking when code is refactored.
        helper().addSourceLines(
                        "Test.java",
                        "import java.lang.reflect.InvocationHandler;",
                        "import java.lang.reflect.Method;",
                        "import java.lang.reflect.InvocationTargetException;",
                        "final class Test implements InvocationHandler {",
                        "  @Override",
                        "  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {",
                        "    try {",
                        "      // BUG: Diagnostic contains: unwrap InvocationTargetException",
                        "      return method.invoke(this, args);",
                        "    } catch (Throwable t) {",
                        "      return handle(t);",
                        "    }",
                        "  }",
                        "  private static Object handle(Throwable t) throws Throwable {",
                        "    if (t instanceof InvocationTargetException) {",
                        "      throw t.getCause();",
                        "    }",
                        "    throw t;",
                        "  }",
                        "}")
                .doTest();
    }

    private CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(InvocationHandlerDelegation.class, getClass());
    }
}
