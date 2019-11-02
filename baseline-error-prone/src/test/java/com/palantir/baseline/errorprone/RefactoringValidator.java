package com.palantir.baseline.errorprone;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.bugpatterns.BugChecker;
import com.sun.tools.javac.main.Main;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RefactoringValidator} delegates to a {@link BugCheckerRefactoringTestHelper},
 * but also validates the output passes validation.
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

        private OutputStage(
                RefactoringValidator helper,
                BugCheckerRefactoringTestHelper.ExpectOutput delegate) {
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
