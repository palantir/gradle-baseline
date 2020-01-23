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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.bugpatterns.BugChecker;

/**
 * {@link RefactoringValidator} delegates to a {@link BugCheckerRefactoringTestHelper}, but also validates the output
 * passes validation.
 */
final class RefactoringValidator {

    private final BugCheckerRefactoringTestHelper delegate;
    private final CompilationTestHelper compilationHelper;
    private String outputPath;
    private String[] outputLines;

    private RefactoringValidator(BugChecker refactoringBugChecker, Class<?> clazz) {
        this.delegate = BugCheckerRefactoringTestHelper.newInstance(refactoringBugChecker, clazz);
        this.compilationHelper = CompilationTestHelper.newInstance(refactoringBugChecker.getClass(), clazz);
    }

    @CheckReturnValue
    static RefactoringValidator of(BugChecker refactoringBugChecker, Class<?> clazz) {
        return new RefactoringValidator(refactoringBugChecker, clazz);
    }

    @CheckReturnValue
    OutputStage addInputLines(String path, String... input) {
        // If expectUnchanged is unused, the input is used as output
        this.outputPath = path;
        this.outputLines = input;
        return new OutputStage(this, delegate.addInputLines(path, input));
    }

    static final class OutputStage {
        private final RefactoringValidator helper;
        private final BugCheckerRefactoringTestHelper.ExpectOutput delegate;

        private OutputStage(RefactoringValidator helper, BugCheckerRefactoringTestHelper.ExpectOutput delegate) {
            this.helper = helper;
            this.delegate = delegate;
        }

        @CheckReturnValue
        TestStage addOutputLines(String path, String... output) {
            helper.outputPath = path;
            helper.outputLines = output;
            return new TestStage(helper, delegate.addOutputLines(path, output));
        }

        @CheckReturnValue
        TestStage expectUnchanged() {
            return new TestStage(helper, delegate.expectUnchanged());
        }
    }

    static final class TestStage {

        private final RefactoringValidator helper;
        private final BugCheckerRefactoringTestHelper delegate;

        private TestStage(RefactoringValidator helper, BugCheckerRefactoringTestHelper delegate) {
            this.helper = helper;
            this.delegate = delegate;
        }

        void doTest() {
            delegate.doTest();
            helper.compilationHelper
                    .addSourceLines(helper.outputPath, helper.outputLines)
                    .doTest();
        }

        void doTest(BugCheckerRefactoringTestHelper.TestMode testMode) {
            delegate.doTest(testMode);
            helper.compilationHelper
                    .addSourceLines(helper.outputPath, helper.outputLines)
                    .doTest();
        }

        void doTestExpectingFailure(BugCheckerRefactoringTestHelper.TestMode testMode) {
            delegate.doTest(testMode);
            assertThatThrownBy(() -> helper.compilationHelper
                            .addSourceLines(helper.outputPath, helper.outputLines)
                            .doTest())
                    .describedAs("Expected the result to fail validation")
                    .isInstanceOf(AssertionError.class);
        }
    }
}
