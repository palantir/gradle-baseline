# Starting a Software Project

Software projects should adhere to a sane set of standards. This document outlines our recommended
standards for all projects, then links to additional technology-specific recommendations.

When starting any new software project, you should:

- [ ] [Use a **source code** management system](#source-control)
- [ ] [Use a **repository hosting** service](#repository-hosting-service)
- [ ] [Set up **Continuous Integration**](#continuous-integration)
- [ ] [Use **Semantic Versioning**](#versioning)
- [ ] [Have **verification and development setup scripts**](#verification-and-development-setup-scripts)

## Source Control

**Use a source code management system**.
[Git](https://en.wikipedia.org/wiki/Git_(software)) is recommended.

Why use source control? Have you ever:

- Made a change to code, realized it was a mistake and wanted to revert back?
- Lost code or had a backup that was too old?
- Had to maintain multiple versions of a product?
- Wanted to see the difference between two (or more) versions of your code?
- Wanted to prove that a particular change broke or fixed a piece of code?
- Wanted to review the history of some code?
- Wanted to submit a change to someone else's code?
- Wanted to share your code, or let other people work on your code?
- Wanted to see how much work is being done, and where, when and by whom?
- Wanted to experiment with a new feature without interfering with working code?

(As outlined on
[Stack Overflow](http://stackoverflow.com/questions/1408450/why-should-i-use-version-control))

## Repository Hosting Service

**Use a repository hosting service**. Using a repository hosting service combined with
[source control](#source-control) allows for a safe development and collaboration story.

[Github](https://github.com/) is recommended.

## Continuous Integration

**Set up Continuous Integration (CI)**. CI tests changes to a project's code base as they're
submitted.

[CircleCI](https://circleci.com/) is recommended.

## Versioning

**Use [Semantic Versioning (Semver)](http://semver.org/)**.

Quick summary of Semantic Versioning:

Given a version number MAJOR.MINOR.PATCH, increment the:

1. MAJOR version when you make incompatible API changes,
2. MINOR version when you add functionality in a backwards-compatible manner, and
3. PATCH version when you make backwards-compatible bug fixes.

Additional labels for pre-release and build metadata are available as
extensions to the MAJOR.MINOR.PATCH format.

## Technology-Specific Checklists

The above recommendations are appropriate for all software development projects. Depending on which
technologies you are working with, you should also read the following:

- [Java Checklist](java.md)
- [Web Application Checklist](web-app.md)
- [Gradle Plugin Checklist](gradle-plugin.md)
