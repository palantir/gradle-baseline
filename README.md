# Baseline Java code quality plugins
[![CircleCI Build Status](https://circleci.com/gh/palantir/gradle-baseline/tree/develop.svg?style=shield)](https://circleci.com/gh/palantir/gradle-baseline)
[![Bintray Release](https://api.bintray.com/packages/palantir/releases/gradle-baseline/images/download.svg) ](https://bintray.com/palantir/releases/gradle-baseline/_latestVersion)

_Baseline is a family of Gradle plugins for configuring Java projects with sensible defaults for code-style, static analysis, dependency versioning, CircleCI and IntelliJ IDEA/Eclipse integration._

| Plugin                                   | Description            |
|------------------------------------------|------------------------|
| `com.palantir.baseline-idea`             | Configures [Intellij IDEA](https://www.jetbrains.com/idea/) with code style and copyright headers
| `com.palantir.baseline-eclipse`          | Configures [Eclipse](https://www.eclipse.org/downloads/) with code style and copyright headers
| `com.palantir.baseline-error-prone`      | Static analysis for your Java code using Google's [error-prone](http://errorprone.info/).
| `com.palantir.baseline-checkstyle`       | Enforces consistent Java formatting using [checkstyle](http://checkstyle.sourceforge.net/)
| `com.palantir.baseline-scalastyle`       | Enforces formatting using [scalastyle](http://www.scalastyle.org/)
| `com.palantir.baseline-class-uniqueness` | Analyses your classpath to ensure no fully-qualified class is defined more than once.
| `com.palantir.baseline-circleci`         | [CircleCI](https://circleci.com/) integration using `$CIRCLE_ARTIFACTS` and `$CIRCLE_TEST_REPORTS` dirs
| `com.palantir.baseline-versions`         | Source dependency versions from a `versions.props` file using [nebula dependency recommender](https://github.com/nebula-plugins/nebula-dependency-recommender-plugin)
| `com.palantir.baseline-config`           | Config files for the above plugins

See also the [Baseline Java Style Guide and Best Practises](./docs).


## Usage
It is recommended to add `apply plugin: 'com.palantir.baseline` to your root project's build.gradle.  Individual plugins will be automatically applied to appropriate subprojects.

```Gradle
buildscript {
    repositories {
        maven { url  "http://palantir.bintray.com/releases" }
    }

    dependencies {
        classpath 'com.palantir.baseline:gradle-baseline-java:<version>'
    }
}

repositories {
    maven { url  "http://palantir.bintray.com/releases" }
}

apply plugin: 'java'
apply plugin: 'org.inferred.processors'  // installs the "processor" configuration needed for baseline-error-prone
apply plugin: 'com.palantir.baseline'
```

Run **`./gradlew baselineUpdateConfig`** to download config files and extract them to the `.baseline/` directory.  These files should be committed to your repository to ensure reproducible builds.

_Tip: Install the [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin to run checkstyle from within IntelliJ._


## Selective usage
Alternatively, you can apply plugins selectively, e.g.:

```Gradle
apply plugin: 'com.palantir.baseline-config'

allprojects {
    apply plugin: 'com.palantir.baseline-idea'
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'com.palantir.baseline-checkstyle'
}
```


## com.palantir.baseline-idea
Run `./gradlew idea` to (re-) generate IntelliJ project and module files from the templates in `.baseline`. The
generated project is pre-configured with Baseline code style settings and support for the CheckStyle-IDEA plugin.

The `com.palantir.baseline-idea` plugin automatically applies the `idea` plugin.

Generated IntelliJ projects have default per-project code formatting rules as well as Checkstyle configuration. The JDK
and Java language level settings are picked up from the Gradle `sourceCompatibility` property on a per-module basis.


## com.palantir.baseline-eclipse
Run `./gradlew eclipse` to repopulate projects from the templates in `.baseline`.

The `com.palantir.baseline-eclipse` plugin automatically applies the `eclipse` plugin, but not the `java` plugin. The
`com.palantir.baseline-eclipse` plugin has no effects if the `java` plugin is not applied.

If set, `sourceCompatibility` is used to configure the Eclipse project settings and the Eclipse JDK version. Note
that `targetCompatibility` is also honored and defaults to `sourceCompatibility`.

Generated Eclipse projects have default per-project code formatting rules as well as Checkstyle configuration.

The Eclipse plugin is compatible with the following versions: Checkstyle 7.5+, JDK 1.7, 1.8


## com.palantir.baseline-error-prone
The `com.palantir.baseline-error-prone` plugin brings in the `net.ltgt.errorprone-javacplugin` plugin. We recommend applying the `org.inferred.processors` plugin 1.12.18+ in order to avoid `error: plug-in not found: ErrorProne`. The minimal setup is as follows:

```groovy
buildscript {
    dependencies {
        classpath 'gradle.plugin.org.inferred:gradle-processors:1.2.18'
    }
}

apply plugin: 'org.inferred.processors'
apply plugin: 'com.palantir.baseline-error-prone'
```

Error-prone rules can be suppressed on a per-line or per-block basis just like Checkstyle rules:

```Java
@SuppressWarnings("Slf4jConstantLogMessage")
```

Rules can be suppressed at the project level, or have their severity modified, by adding the following to the project's `build.gradle`:

```gradle
tasks.withType(JavaCompile).configureEach {
    options.errorprone.errorproneArgs += ['-Xep:Slf4jLogsafeArgs:OFF']
    // alternatively, using the DSL:
    options.errorprone {
        check('Slf4jLogsafeArgs', CheckSeverity.OFF)
    }
}
```

More information on error-prone severity handling can be found at [errorprone.info/docs/flags](http://errorprone.info/docs/flags).

#### Baseline error-prone checks
Baseline configures the following checks in addition to the [error-prone's out-of-the-box
checks](https://errorprone.info):

- Slf4jConstantLogMessage: Allow only compile-time constant slf4j log message strings.
- Slf4jLogsafeArgs: Allow only com.palantir.logsafe.Arg types as parameter inputs to slf4j log messages. More information on
Safe Logging can be found at [github.com/palantir/safe-logging](https://github.com/palantir/safe-logging).


## com.palantir.baseline-checkstyle
Checkstyle rules can be suppressed on a per-line or per-block basis. (It is good practice to first consider formatting
the code block in question according to the project's style guidelines before adding suppression statements.) To
suppress a particular check, say `MagicNumberCheck`, from an entire class or method, annotate the class or method with
the lowercase check name without the "Check" suffix:

```Java
@SuppressWarnings("checkstyle:magicnumber")
```

Checkstyle rules can also be suppressed using comments, which is useful for checks such as `IllegalImport` where
annotations cannot be used to suppress the violation. To suppress checks for particular lines, add the comment
`// CHECKSTYLE:OFF` before the first line to suppress and add the comment `// CHECKSTYLE:ON` after the last line.

To disable certain checks for an entire file, apply [custom suppressions](http://checkstyle.sourceforge.net/config.html)
in `.baseline/checkstyle/checkstyle-suppressions`.

### Copyright Checks

By default Baseline enforces Palantir copyright at the beginning of files. To change this, edit the template copyright
in `.baseline/copyright/*.txt` and the RegexpHeader checkstyle configuration in `.baseline/checkstyle/checkstyle.xml`


## com.palantir.baseline-class-uniqueness
Run `./gradlew checkClassUniqueness` to scan all jars on the `runtime` classpath for identically named classes.
This task will run automatically as part of `./gradlew build`.

If you discover multiple jars on your classpath contain clashing classes, you should ideally try to fix them upstream and then depend on the fixed version.  If this is not feasible, you may be able to tell Gradle to [use a substituted dependency instead](https://docs.gradle.org/current/userguide/customizing_dependency_resolution_behavior.html#sec:module_substitution):

```gradle
configurations.all {
    resolutionStrategy.eachDependency { DependencyResolveDetails details ->
        if (details.requested.name == 'log4j') {
            details.useTarget group: 'org.slf4j', name: 'log4j-over-slf4j', version: '1.7.10'
            details.because "prefer 'log4j-over-slf4j' over any version of 'log4j'"
        }
    }
}
```


## com.palantir.baseline-circleci
Automatically applies the following plugins:

- [`com.palantir.configuration-resolver`](https://github.com/palantir/gradle-configuration-resolver-plugin) - this adds a `./gradlew resolveConfigurations` task which is useful for caching on CI.

Also, the plugin:

1. stores junit test reports in `$CIRCLE_TEST_REPORTS/junit`
2. Converts java compilation errors and checkstyle errors into test failures stored under `$CIRCLE_TEST_REPORTS/javac` and `$CIRCLE_TEST_REPORTS/checkstyle` respectively
![CHECKSTYLE — 1 FAILURE](images/checkstyle-circle-failure.png?raw=true "CircleCI failure image")
3. stores the HTML output of tests in `$CIRCLE_ARTIFACTS/junit`
4. stores the HTML reports from `--profile` into `$CIRCLE_ARTIFACTS/reports`


## com.palantir.baseline-versions
Sources version numbers from a root level `versions.props` file.  This plugin should be applied in an `allprojects` block. It is effectively a shorthand for the following:

```gradle
buildscript {
    dependencies {
        classpath 'com.netflix.nebula:nebula-dependency-recommender:x.y.z'
    }
}

allprojects {
    apply plugin: 'nebula.dependency-recommender'
    dependencyRecommendations {
        strategy OverrideTransitives // use ConflictResolved to undo this
        propertiesFile file: project.rootProject.file('versions.props')
        if (file('versions.props').exists()) {
            propertiesFile file: project.file('versions.props')
        }
    }
}
```

Features from [nebula.dependency-recommender](https://github.com/nebula-plugins/nebula-dependency-recommender-plugin) are still available (for now), so you can configure BOMs:

```gradle
dependencyRecommendations {
    mavenBom module: 'com.palantir.product:your-bom'
}
```
