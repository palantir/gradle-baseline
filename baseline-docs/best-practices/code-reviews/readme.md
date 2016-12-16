# Code Reviews

![alt codeQuality](https://imgs.xkcd.com/comics/code_quality.png "Code Quality")

This guide covers the following topics:

- [What to Review](#what-to-review)
- [Preparing Code for Review](#preparing-code-for-review)
- [Writing Useful Commit Messages](#writing-useful-commit-messages)
- [Doing Code Reviews](#doing-code-reviews)
  - [Purpose](#purpose)
  - [Implementation](#implementation)
  - [Legibility & Style](#legibility-style)
  - [Maintainability](#maintainability)
  - [Review Comments: Concise and Actionable](#review-comments-concise-and-actionable)
- [Responding to Reviews](#responding-to-reviews)
- [Code Review Examples](#code-review-examples)
  - [Example: Inconsistent Naming](#example-inconsistent-naming)
  - [Example: Inconsistent Method Signatures](#example-inconsistent-method-signatures)
  - [Example: Code Style Violation](#example-code-style-violation)
  - [Example: Library Use](#example-library-use)
  - [Example: Potential Bug](#example-potential-bug)

We do code reviews (CRs) to **ensure high code quality** and to **reduce the
number of bugs** we ship. High-quality code is **clean**, **correct**,
and **scalable**. In more detail, reviews serve several important
causes:

- **Committers are motivated** subconsciously by the notion of a set
  of reviewers who will look over the change request: the committer tends to
  clean up loose ends, consolidate TODOs, and generally improve the commit.

- **Sharing knowledge** helps development teams in at least three
  ways:

  - A CR explicitly communicates added/altered/removed functionality to
    team members who can subsequently build on the work done.

  - The committer may use a technique or algorithm that reviewers can
    learn from.

  - Reviewers may possess knowledge about the code base that can help
    improve or consolidate the change; for example, someone else may be
    concurrently working on a similar feature or fix.

- **Consistency** in a code base makes code easier to read and
  understand, helps prevent bugs, and facilitates collaboration
  between regular and migratory developer species.

- **Legibility** of code fragments is hard to judge for the author
  whose brainchild it is, and easy to judge for a reviewer who does
  not have the full context. Legible code is more reusable, bug-free,
  and future-proof.

- **Accidental errors** (e.g., typos) as well as **structural errors**
  (e.g., dead code, logic or algorithm bugs) are often much easier to
  spot for critical reviewers with an outside perspective. (Often,
  it's a rather satisfying sport to find bugs...)

## What to Review

There is no eternally true answer to this question and the right
approach should be agreed on by each development team; some teams prefer
to review *every* CR merged into the develop branch, while others will have a
"triviality" threshold under which a review is not required. The
trade-off is between effective use of engineers' (both authors' and
reviewers') time and maintaining code quality.

## Preparing Code for Review

It is the author's duty to submit CRs that are easy to review in order
not to waste the reviewers' time and motivation. A few guidelines:

- **Scope and size:** Changes have a narrow, well-defined, self-contained
  scope that they cover exhaustively. For example, a CR may implement
  a new feature or fix a bug. Shorter CRs are preferred over
  longer CRs. If a CR makes substantive changes to more than ~5
  files, or took longer than 1-2 days to write, or would take more
  than 20mins to review, consider splitting it into multiple CRs that
  are each self-contained. For example, a developer can submit one CR that
  defines the API for a new feature in terms of interfaces and
  documentation and a second CR that adds an implementation for
  those interfaces.

  For features or code contributions that only make sense as one unit
  but that are too large to fit into one reasonable CR, consider a
  multi-branch CR model: Create a main branch `feature/big-feature`
  and a number of secondary branches -- e.g.,
  `feature/big-feature-api`, `feature/big-feature-testing`, etc. --
  that each encapsulate a subset of the functionality and that get
  individually code-reviewed against the `feature/big-feature` branch.
  Once all secondary branches are merged into `feature/big-feature`,
  create a CR for merging the latter into `develop`. The functional
  equivalent in Gerrit is a series of reviewed commits against a
  single `feature/big-feature` branch.

- Only submit **complete**, **self-reviewed** (by diff), and **self-tested** CRs.
  In order to save reviewers' time, test the submitted changes (i.e., run your code),
  make sure they pass all builds as well as all tests and code quality
  checks -- both locally and on the build servers -- *before assigning
  reviewers*.

- **Refactoring CRs:** Refactoring CRs may *never* alter behavior;
  conversely, a behavior-changing CR should avoid refactoring and code
  formatting changes. There are multiple good reasons for this:

  - Refactoring CRs are normally very large and will consequently be
    reviewed at a different depth. Unintended behavior changes can leak into
    the code base without anyone noticing.

  - Large refactoring CRs are not compatible with cherry-picking,
    rebasing, and other source control magic. It is very hard to undo a
    behavior change that was introduced as part of a refactoring commit.

  - Expensive, human review-time should be spent on the program logic
    rather than, say, its formatting or the import order. Style issues can be
    fixed prior to code reviews with tooling like
    [gradle-baseline](https://github.com/palantir/gradle-baseline).

## Writing Useful Commit Messages

Here's a widely quoted, sensible standard: "[A note about git commit
messages](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)."
An example of a commit message that follows this standard:

``` {.sourceCode .text}
Capitalized, short (80 chars or less) summary

More detailed explanatory text, if necessary.  Wrap it to about 120 characters or so.  In some contexts, the first
line is treated as the subject of an email and the rest of the text as the body.  The blank line separating the
summary from the body is critical (unless you omit the body entirely); tools like rebase can get confused if you run
the two together.

Write your commit message in the imperative: "Fix bug" and not "Fixed bug" or "Fixes bug."  This convention matches
up with commit messages generated by commands like git merge and git revert.

Further paragraphs come after blank lines.

- Bullet points are okay, too

- Typically a hyphen or asterisk is used for the bullet, followed by a single space, with blank lines in between,
    but conventions vary here

- Use a hanging indent
```

Try to describe both **what** the commit changes and **how** it does it:

``` {.sourceCode .text}
> BAD. Don't do this.
Make PhoenixApp work out of IntelliJ

> Good.
Add jcsv dependency to fix IntelliJ compilation
```

## Doing Code Reviews

**Promptly**: A code review is a synchronization point between different
team members and can thus easily block progress.
Consequently, code reviews need to be prompt (on the order of
hours, not days), and team members and leads need to be aware of
the time commitment and prioritize review time accordingly. If you
don’t think you can complete a review in time, please let the CR
author know right away so they can find someone else.

**Thoroughly**: A review should be thorough enough that the reviewer could
explain the change at a reasonable level of detail to another developer if necessary. This
is the bar for a very practical reason: one of the goals with
introducing full-time code reviews is that the knowledge of a particular
feature area will not rest with a single person. This is a good thing
because it will allow you to take vacations without having to worry
about issues arising in your area and also provides a sounding board
for discussing implementation ideas with someone else who is caught up
on your area.

As a reviewer, it is your responsibility to enforce coding standards and
keep the quality bar up. Reviewing code is more of an art than a
science. The only way to learn it is to do it; an experienced
reviewer should consider putting other less experienced reviewers on
their changes and have them do a review first. Assuming the author has
followed the guidelines above (especially with respect to self-review
and ensuring the code runs), here's an list of things a developer should
consider in a code review:

### Purpose

- **Does this code accomplish the author's purpose?** Every change should
  have a specific reason (new feature, refactor, bugfix, etc). Does the
  submitted code actually accomplish this purpose?

- **Ask questions.** A reviewer can give better reviews with a better
  understanding of the code. Sometimes, having to ask the question at
  all is an indication that the code needs to be rewritten or
  supported with comments/tests.

### Implementation

- **Think about how you would have solved the problem.** If it's
  different, why is that? Does your code handle more cases? Is it
  shorter/easier/cleaner/faster/safer yet equally functional? Is there
  some underlying pattern you spotted that isn't captured by the
  current code?

- **Think like an adversary.** Try to "catch" authors taking shortcuts or
  missing cases by coming up with problematic configurations/input
  data that breaks their code.

- **Think about libraries or existing product code.** When someone
  re-implements existing functionality, more often than not it's
  simply because they don't know it already exists. If it's
  purposeful, adding a comment to justify it is usually helpful. As a
  bonus, vetted libraries often have opinions on structure/design that
  are backed by experience.

- **Does this add compile-time or run-time dependencies (especially
  between sub-projects)?** We want to keep our products loosely coupled,
  with as few dependencies as possible. Any build.gradle changes
  should be scrutinized heavily.

### Legibility and Style

- **Think about your reading experience.** Did you grasp the concepts in a
  reasonable amount of time? Was the flow sane and names easy to
  follow? Were you able to keep track through multiple files or
  functions? Were you put off by inconsistent naming?

- **Does the code adhere to coding guidelines and code style?** Is the
  code consistent with the project in terms of style, API conventions,
  etc?

- **Does this code have TODOs?** TODOs just pile up in code, and become
  stale over time. Have the author submit a ticket on Github Issues and
  attach the issue number to the TODO. Similarly, the CR should not have
  large amounts of commented-out code.

### Maintainability

- **Read the tests.** If there are no tests and there should be, ask the
  author to write some. Untestable features are rare, while untestable
  implementations of features are fairly common. Check the tests
  themselves: are they covering interesting cases? Are they readable?
  Does the CR lower overall test coverage? Think of ways this code
  could break. Style standards for tests are often different than core
  code, but still important.

- **Does this CR have the possibility of breaking test code - Bouncers
  or integrations tests?** These are often not checked as part of the
  commit/merge checks, but having them go down is painful
  for everyone. Specific things to look for are: removal of test
  utilities or modes, changes in configuration, and changes in
  artifact layout/structure.

- **Does this change break backward compatibility?** If so, is it OK to
  merge the CR at this point?

- **Does this code need integration tests?** Sometimes, code can't
  adequately be tested with unit tests alone - especially if the code
  needs to interact with outside systems. In this case, make sure that
  the coder either has integration tests in place or a bug filed to
  add them.

- **Leave feedback on documentation, comments, and commit messages.**
  Redundant comments clutter the code, while terse commit messages
  mystify future readers. This isn't always applicable, but quality
  comments and commit messages will pay for themselves down the line.
  (Think of a time you saw an excellent, or truly terrible, commit
  message or comment.)

- **Was the external documentation updated?**
  If your project maintains a readme, changelog, or other documentation,
  was it updated to reflect any changes? Outdated documentation can be
  more confusing than none, and it will be more costly to fix in the
  future than keeping it up to date right now.

Declining or disapproving a CR is not rude. If the change is redundant
or irrelevant due to age, decline it with an explanation. If you
consider it unmergable due to one or more fatal flaws, disapprove it,
again with an explanation. Sometimes the right outcome of a CR is "let's
do this a totally different way" or even "let's not do this at all."

Be respectful to your reviewees! While adversarial thinking is handy,
it's not your feature and you can't make all the decisions. If you
can't come to an agreement with your reviewee with the code as-is,
switch to real-time communication or even involve a lead if necessary.

#### Review Comments: Concise and Actionable

Comments should be **concise and neutral**. Try to differentiate
between suggestions (e.g., "Suggestion: extract method to improve
legibility"), required changes (e.g., "Add @Override"), and points that
need discussion or clarification (e.g., "Is this really the correct
behavior? If so, please add a comment explaining the logic."). When
you're done with a code review, indicate to what extent you expect the
author to respond to your comments and whether you would like to
re-review the CR after the changes have been implemented (e.g., "Feel
free to merge after responding to the few minor suggestions" vs. "Please
consider my suggestions and let me know when I can take another look.").

## Responding to Reviews

Part of the purpose of the code review is improve the author's change
request; consequently, don't be offended by your reviewer's suggestions
and take them seriously even if you don't agree. As mentioned above, if
you can't come to an agreement with the reviewer, switch to real-time
communication or even involve a lead if necessary.

## Code Review Examples

In the following examples, suggested review comments are indicated by
`//R: ...` comments in the code blocks.

### Example: Inconsistent Naming

``` {.sourceCode .java}
class MyClass {
    private int countTotalPageVisits; //R: name variables consistently
    private int uniqueUsersCount;

    ...
}
```

### Example: Inconsistent Method Signatures

``` {.sourceCode .java}
interface MyInterface {
    /** Returns {@code Optional.absent()} if string cannot be extracted. */
    public Optional<String> extractString(String s);

    /** Returns null if {@code s} cannot be rewritten. */
    public String rewriteString(String s); //R: harmonize error case return value with extractString() method
}
```

### Example: Code Style Violation

``` {.sourceCode .java}
class MyClass {
  int counter; //R: indent by 4 spaces
}
```

### Example: Library Use

``` {.sourceCode .java}
//R: remove and replace by Guava's MapJoiner
String joinAndConcatenate(Map<String, String> map, String keyValueSeparator, String keySeparator);
```

### Example: Potential Bug

``` {.sourceCode .java}
int i = 0;
for (String s : allStrings) {
    log.debug("String number " + i++ + " is: " + s); //R: possible bug: review the counting logic
    ...
)
```
