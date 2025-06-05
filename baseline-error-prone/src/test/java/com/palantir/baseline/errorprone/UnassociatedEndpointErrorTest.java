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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnassociatedEndpointErrorTest {

    private CompilationTestHelper compilationTestHelper;
    private static final String ENDPOINT_SERVICE_EXCEPTION =
            // language=java
            """
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

public abstract class EndpointServiceException extends RuntimeException implements SafeLoggable {
    private static final String EXCEPTION_NAME = "EndpointServiceException";
    private final ErrorType errorType;
    private final List<Arg<?>> args; // This is an unmodifiable list.
    private final String errorInstanceId;
    private final String unsafeMessage;
    private final String noArgsMessage;

    public EndpointServiceException(ErrorType errorType, Arg<?>... parameters) {
        this(errorType, null, parameters);
    }

    public EndpointServiceException(ErrorType errorType, @Nullable Throwable cause, Arg<?>... args) {
        super(cause);
        this.errorInstanceId = generateErrorInstanceId(cause, Set.of());
        this.errorType = errorType;
        this.args = arrayToUnmodifiableList(args);
        this.unsafeMessage = renderUnsafeMessage(EXCEPTION_NAME, errorType, args);
        this.noArgsMessage = renderNoArgsMessage(EXCEPTION_NAME, errorType);
    }

    public final ErrorType getErrorType() {
        return errorType;
    }

    public final String getErrorInstanceId() {
        return errorInstanceId;
    }

    @Override
    public final String getMessage() {
        return unsafeMessage;
    }

    @Override
    public final String getLogMessage() {
        return noArgsMessage;
    }

    @Override
    public final List<Arg<?>> getArgs() {
        return args;
    }

    private static String generateErrorInstanceId(
            @Nullable Throwable cause,
            Set<Throwable> dejaVu) {
        if (cause == null || !dejaVu.add(cause)) {
            return UUID.randomUUID().toString();
        }
        if (cause instanceof ServiceException) {
            return ((ServiceException) cause).getErrorInstanceId();
        }
        if (cause instanceof EndpointServiceException) {
            return ((EndpointServiceException) cause).getErrorInstanceId();
        }
        if (cause instanceof RemoteException) {
            return ((RemoteException) cause).getError().errorInstanceId();
        }
        return generateErrorInstanceId(cause.getCause(), dejaVu);
    }

    private static <T> List<T> arrayToUnmodifiableList(T[] elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>(elements.length);
        for (T item : elements) {
            if (item != null) {
                list.add(item);
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static String renderNoArgsMessage(String exceptionName, ErrorType errorType) {
        return exceptionName + ": " + errorType.code() + " (" + errorType.name() + ")";
    }

    private static String renderUnsafeMessage(String exceptionName, ErrorType errorType, Arg<?>... args) {
        String message = renderNoArgsMessage(exceptionName, errorType);

        if (args == null || args.length == 0) {
            return message;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(message).append(": {");
        boolean first = true;
        for (Arg<?> arg : args) {
            if (arg == null) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append(arg.getName()).append("=").append(arg.getValue());
        }
        builder.append("}");

        return builder.toString();
    }
}
""";

    @BeforeEach
    void beforeEach() {
        compilationTestHelper = CompilationTestHelper.newInstance(UnassociatedEndpointErrorV2.class, getClass());
    }

    @SuppressWarnings({"checkstyle:MethodLength", "MisformattedTestData"})
    @Test
    // @Disabled
    void testFindsRuntimeException() {
        compilationTestHelper
                .addSourceLines("EndpointServiceException.java", ENDPOINT_SERVICE_EXCEPTION)
                .addSourceLines(
                        "ServerErrors.java",
                        // language=java
                        """
                        import javax.annotation.processing.Generated;
                        import com.palantir.logsafe.Safe;
                        import javax.annotation.Nullable;
                        import com.palantir.logsafe.SafeArg;
                        import com.palantir.conjure.java.api.errors.ErrorType;
                        @Generated("com.palantir.conjure.java.types.CheckedErrorGenerator")
                        public final class ServerErrors {
                           private ServerErrors() {}
                           public static final ErrorType MY_ERROR =
                                    ErrorType.create(ErrorType.Code.INVALID_ARGUMENT, "Errors:MyError");
                           public static MyErr myError(@Safe String arg,@Nullable Throwable cause) {
                                return new MyErr(arg, cause);
                            }
                           public static final class MyErr extends EndpointServiceException {
                             private MyErr(
                               @Safe String arg, @Nullable Throwable cause) {
                                 super(MY_ERROR, cause, SafeArg.of("arg", arg));
                             }
                           }
                        }""")
                .addSourceLines(
                        "UndertowService.java",
                        // language=java
                        """
                        import com.palantir.tokens.auth.AuthHeader;
                        import javax.annotation.processing.Generated;
                        @Generated("com.palantir.conjure.java.services.UndertowServiceInterfaceGenerator")
                        public interface UndertowService {
                            // void endpoint(AuthHeader authHeader) throws ServerErrors.MyErr;
                            void endpointTwo(AuthHeader authHeader) throws ServerErrors.MyErr;
                        }""")
                .addSourceLines(
                        "UndertowServiceImpl.java",
                        // language=java
                        """
                        import com.palantir.tokens.auth.AuthHeader;
                        import javax.annotation.processing.Generated;
                        public class UndertowServiceImpl implements UndertowService {
//                            @Override
//                            public void endpoint(AuthHeader authHeader) {
//                                throw ServerErrors.myError("arg", null);
//                            }

                            @Override
                            // BUG: Diagnostic contains: Endpoint method can throw unassociated EndpointServiceException subtypes: ServerErrors.MyErr
                            public void endpointTwo(AuthHeader authHeader) {
                                throwError();
                            }

                            private void throwError() {
                              try {
                                throw ServerErrors.myError("arg", null);
                               } catch (ServerErrors.MyErr e) {
                                  // do nothing
                               }
                            }
                        }
                        """)
                .doTest();
    }
}
