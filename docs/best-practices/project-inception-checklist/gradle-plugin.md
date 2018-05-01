# Gradle Plugin Checklist

- [ ] [Before Starting](#before-starting)
- [ ] [Use **Maven Coordinates / Naming Conventions**](#maven-coordinates-naming-conventions)
- [ ] [Publishing](#publishing)
- [ ] [Compliance](#compliance)
- [ ] [Documentation](#documentation)
- [ ] [Testing](#testing)

## Before Starting

- Before creating a new Gradle Plugin, check if a similar bundle already exists
  (including, but not limited to):
  - [Nebula Plugins](http://nebula-plugins.github.io/)
  - [Gradle Plugin Repository](https://plugins.gradle.org/)

## Maven Coordinates / Naming Conventions

- The groupId should be ``com.<organization-name>.<project>`` where ``<project>`` does not contain
more ``.s``
- The groupId should not contain underscores
- The artifactId should be ``gradle-<project>-plugin``
- The plugin name should be ``com.<organization-name>.<project>`` where the project name may include
 dashes

## Open Source Plugins

### Publishing

- Should be a public repository on [github.com](https://github.com/)
- Should be added to [Gradle Plugin Repository](https://plugins.gradle.org/) listing
- Releases should be published to JCenter
- Snapshots should be published to OSS JFrog
- Should be tested + published by CircleCI

### Compliance

- Should have a CONTRIBUTING.md file that is approved by your organization
- Should have the Contributor's License Agreement PDFs available
- Should be made available under the Apache 2.0 License in a LICENSE file

## Documentation

- Usage examples should be in the README.md

## Testing

- The project should have integration tests using the
  [nebula-test](https://github.com/nebula-plugins/nebula-test) library.
