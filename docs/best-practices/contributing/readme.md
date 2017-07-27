# Contributing to repositories

This section summarizes best practices for contributing to code repositories.

*TL;DR: Reach out to the repository owners before making a pull-request, keep contributions focused.*

### Discuss the scope of your contribution
Whether contributing to a repository owned by your team, or to another team's product or library, the first step should
almost always (see below for exceptions) be to reach out to the team and discuss your contribution:

- Read project-specific contribution notes in the README or CONTRIBUTING file
- Check the relevant GitHub or JIRA issues for related work
- Send an email, comment on a relevant ticket, or discuss your contribution over a beer

Small contributions like fixes for typos or handful-of-lines cosmetic changes likely don't require prior discussion.
Just open a pull-request and comment with a friendly message of the form *"Hey @person, I am trying to fix bug #123 here
and have also found a minor typo in the README. Let me know what you think or whether you want me to change something."*

Any substantial change (including additional features to a library or service, API modifications or additions, changes
with performance impact, additional dependencies, etc.) mandate prior discussion. This conversation is a great
opportunity to discuss how the proposed change aligns with the team's timeline and goals, whether its architecture and
implementation is sound, whether someone else is already working on the same issue, etc.


### Separate refactoring steps from actual contributions
Though this is general recommendation for all code changes, contributions to other teams' repositories in particular
should not conflate feature/fix work with unrelated refactoring work. If your contribution requires a refactoring step,
it is often a good idea to first submit a refactoring-only contribution, and then a follow-up contribution with the
feature or fix.


### Match existing code style
Repositories owned by other teams may not always follow your preferred code style. Try to match the existing code style
and avoid refactoring/restyling existing code unless necessary for your change (cf., [Separate refactoring and other
contributions](#separate-refactoring-and-other-contributions)).


### Own the contribution until it is merged
The contributor is generally responsible for shepherding a contribution or pull-request until it is merged. This may
require pinging code reviewers, rebasing a pull-request when necessary, making the requested changes, adding
documentation and tests, etc. Project owners may reserve the right to eventually close idle contributions, so make sure
to get it merged in a timely fashion.

Suggestions for efficient and effective code reviews, as well as responsibilities of the reviewer or project owner, are
discussed in the [best-practices document on Code Reviews](../code-reviews/readme.md).


### Keep contributions as small and focused as possible
Generally, we prefer small and focused contributions over bulky, vast ones. A rough guide line is that a contribution
to another team's repository should not be longer than 200-300 lines unless
[explicitly agreed and discussed](#discuss-the-scope-of-your-contribution).

A common pattern is to split a contribution into several, isolated pieces and submit them as independent pull-requests.
For example, if a contribution comprises a refactoring step, adds a new storage component, and exposes a new external
API, then it can be split into three or four pieces: 

- **[1]** a refactoring step (not changing any logic or behavior)
- **[2]** the implementation of the storage layer (plus tests)
- **[3a]** a proposal for the external API (this will likely not get merged without [3b])
- **[3b]** the implementation of the API, connecting the storage layer from [2] with the API from [3a]
