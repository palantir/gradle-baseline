# Java Checklist

- [ ] [Have a **Gradle build system**](#build-system)
- [ ] [Use gradle-baseline for **code quality**](#code-quality)
- [ ] [Enforce **test coverage**](#test-coverage)

## Build System

Use [Gradle](http://gradle.org/).

Why Gradle? See [here](http://gradle.org/whygradle-build-automation/).

## Code Quality

Use [gradle-baseline](https://github.com/palantir/gradle-baseline) to configure code-quality tools
for your project.

## Test Coverage

Java projects should enforce at least 70% test coverage through
[Jacoco](https://github.com/palantir/gradle-jacoco-coverage).

See [Tests](../testing-software/readme.md) for an introduction to
effectively testing applications.

