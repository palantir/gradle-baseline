/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import org.junit.jupiter.api.Test;

class PreferUncheckedIoExceptionTest {

    private RefactoringValidator fix() {
        return RefactoringValidator.of(PreferUncheckedIoException.class, getClass());
    }

    @Test
    void wrapRuntimeException() {
        fix().addInputLines("Test.java", """
            import com.palantir.logsafe.exceptions.SafeRuntimeException;

            class Test {
                Throwable wrap1(RuntimeException e) {
                    return new RuntimeException(e);
                }

                Throwable wrap2(RuntimeException e) {
                    return new RuntimeException("message", e);
                }

                Throwable wrap3(RuntimeException e) {
                    return new SafeRuntimeException(e);
                }

                Throwable wrap4(RuntimeException e) {
                    return new SafeRuntimeException("message", e);
                }
            }
            """.split("\n")).expectUnchanged().doTest();
    }

    @Test
    void wrapIoException() {
        fix().addInputLines("Test.java", """
            import com.palantir.logsafe.SafeArg;
            import com.palantir.logsafe.exceptions.SafeRuntimeException;
            import java.io.IOException;

            class Test {
                Throwable wrap1(IOException e) {
                    return new RuntimeException(e);
                }

                Throwable wrap2(IOException e) {
                    return new RuntimeException("message", e);
                }

                Throwable wrap3(IOException e) {
                    return new SafeRuntimeException(e);
                }

                Throwable wrap4(IOException e) {
                    return new SafeRuntimeException("message", e, SafeArg.of("name", "value"));
                }
            }
            """.split("\n"))
                .addOutputLines("Test.java", """
                    import com.palantir.logsafe.SafeArg;
                    import com.palantir.logsafe.exceptions.SafeRuntimeException;
                    import com.palantir.logsafe.exceptions.SafeUncheckedIoException;
                    import java.io.IOException;
                    import java.io.UncheckedIOException;

                    class Test {
                        Throwable wrap1(IOException e) {
                            return new UncheckedIOException(e);
                        }

                        Throwable wrap2(IOException e) {
                            return new UncheckedIOException("message", e);
                        }

                        Throwable wrap3(IOException e) {
                            return new SafeUncheckedIoException(e);
                        }

                        Throwable wrap4(IOException e) {
                            return new SafeUncheckedIoException("message", e, SafeArg.of("name", "value"));
                        }
                    }
                    """.split("\n"))
                .doTest();
    }

    @Test
    void wrapCustomIoException() {
        fix().addInputLines("Test.java", """
            import com.palantir.logsafe.SafeArg;
            import com.palantir.logsafe.exceptions.SafeRuntimeException;
            import java.io.IOException;

            class Test {
                class CustomIoException extends IOException {}

                Throwable wrap1(CustomIoException e) {
                    return new RuntimeException(e);
                }

                Throwable wrap2(CustomIoException e) {
                    return new RuntimeException("message", e);
                }

                Throwable wrap3(CustomIoException e) {
                    return new SafeRuntimeException(e);
                }

                Throwable wrap4(CustomIoException e) {
                    return new SafeRuntimeException("message", e, SafeArg.of("name", "value"));
                }
            }
            """.split("\n"))
                .addOutputLines("Test.java", """
                    import com.palantir.logsafe.SafeArg;
                    import com.palantir.logsafe.exceptions.SafeRuntimeException;
                    import com.palantir.logsafe.exceptions.SafeUncheckedIoException;
                    import java.io.IOException;
                    import java.io.UncheckedIOException;

                    class Test {
                        class CustomIoException extends IOException {}

                        Throwable wrap1(CustomIoException e) {
                            return new UncheckedIOException(e);
                        }

                        Throwable wrap2(CustomIoException e) {
                            return new UncheckedIOException("message", e);
                        }

                        Throwable wrap3(CustomIoException e) {
                            return new SafeUncheckedIoException(e);
                        }

                        Throwable wrap4(CustomIoException e) {
                            return new SafeUncheckedIoException("message", e, SafeArg.of("name", "value"));
                        }
                    }
                    """.split("\n"))
                .doTest();
    }
}
