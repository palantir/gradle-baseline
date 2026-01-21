<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-baseline"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# Baseline Java code quality plugins
[![CircleCI Build Status](https://circleci.com/gh/palantir/gradle-baseline/tree/develop.svg?style=shield)](https://circleci.com/gh/palantir/gradle-baseline)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/palantir/baseline/com.palantir.baseline.gradle.plugin/maven-metadata.xml.svg?label=plugin&logo=gradle)](https://plugins.gradle.org/plugin/com.palantir.baseline)

_Baseline is a family of Gradle plugins for configuring Java projects with sensible defaults for code-style, static analysis, dependency versioning, CircleCI and IntelliJ IDEA integration._

| Plugin                                             | Description            |
|----------------------------------------------------|------------------------|
| `com.palantir.baseline-idea`                       | Configures [Intellij IDEA](https://www.jetbrains.com/idea/) with code style and copyright headers
| `com.palantir.baseline-checkstyle`                 | Enforces consistent Java formatting using [checkstyle](http://checkstyle.sourceforge.io/)
| `com.palantir.baseline-format`                     | Formats your java files to comply with checkstyle
| `com.palantir.baseline-scala`                      | Enforces formatting using [scalastyle](https://github.com/scalastyle/scalastyle)
| `com.palantir.baseline-class-uniqueness`           | Analyses your classpath to ensure no fully-qualified class is defined more than once.
| `com.palantir.baseline-circleci`                   | [CircleCI](https://circleci.com/) integration using `$CIRCLE_ARTIFACTS` and `$CIRCLE_TEST_REPORTS` dirs
| `com.palantir.baseline-config`                     | Config files for the above plugins
| `com.palantir.baseline-reproducibility`            | Sensible defaults to ensure Jar, Tar and Zip tasks can be reproduced
| `com.palantir.baseline-exact-dependencies`         | Ensures projects explicitly declare all the dependencies they rely on, no more and no less
| `com.palantir.baseline-encoding`                   | Ensures projects use the UTF-8 encoding in compile tasks.
| `com.palantir.baseline-testing`                    | Configures test tasks to dump heap dumps (hprof files) for convenient debugging
| `com.palantir.baseline-test-heap`                  | Increases the default Test task heap from 512m to 2g.
| `com.palantir.baseline-java-compiler-diagnostics`  | Applies the `-Xmaxwarns` and `-Xmaxwarns` compiler options with a very large limit to avoid truncating failure info.
| `com.palantir.baseline-java-compiler-heap`         | Increases the default JavaCompile task heap from 512m to 2g.
| `com.palantir.baseline-java-properties`            | Applies the `-parameters` compiler option to include additional metadata for reflection on method parameters.
| `com.palantir.baseline-immutables`                 | Enables incremental compilation for the [Immutables](http://immutables.github.io/) annotation processor.
| `com.palantir.baseline-module-jvm-args`            | Propagate and collect required exports from transitive dependencies, and applies them to compilation and execution for runtime dependencies.
| `com.palantir.baseline-java-versions`              | Configures JDK versions in a consistent way via Gradle Toolchains.
| `com.palantir.baseline-prefer-project-modules`     | Configures Gradle to prefer project modules over external modules on dependency resolution per default.

See also the [Baseline Java Style Guide and Best Practices](./docs).

> [!NOTE]
> baseline-error-prone and baseline-null-away now live in their [own repository](https://github.com/palantir/baseline-error-prone)


## Usage
The baseline set of plugins requires at least Gradle 6.1.

It is recommended to add `apply plugin: 'com.palantir.baseline'` to your root project's build.gradle.  Individual plugins will be automatically applied to appropriate subprojects.

```Gradle
buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    dependencies {
        classpath 'com.palantir.baseline:gradle-baseline-java:<version>'
        classpath 'gradle.plugin.org.inferred:gradle-processors:2.1.0'
    }
}

repositories {
    mavenCentral()
}

apply plugin: 'java'
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


### Copyright Checks

Baseline enforces Palantir copyright at the beginning of files when applying `com.palantir.baseline-format`. To change this, edit the template copyrights
in `.baseline/copyright/*.txt`. The largest file (sorted lexicographically) will be used to generate a new copyright if one is missing, or none of the existing templates match.

To automatically update all files with mismatching/missing copyrights, run `./gradlew format`.

## com.palantir.baseline-class-uniqueness
When applied to a java project, this inspects all the jars in your `runtimeClasspath` configuration and records any conflicts to a `baseline-class-uniqueness.lock` file. For example:

```
# Danger! Multiple jars contain identically named classes. This may cause different behaviour depending on classpath ordering.
# Run ./gradlew checkClassUniqueness --write-locks to update this file

## runtimeClasspath
[jakarta.annotation:jakarta.annotation-api, javax.annotation:javax.annotation-api]
  - javax.annotation.Resource$AuthenticationType
[jakarta.ws.rs:jakarta.ws.rs-api, javax.ws.rs:javax.ws.rs-api]
  - javax.ws.rs.BadRequestException
  - javax.ws.rs.ClientErrorException
  - javax.ws.rs.ForbiddenException
  - javax.ws.rs.InternalServerErrorException
  - javax.ws.rs.NotAcceptableException
  - javax.ws.rs.NotAllowedException
  - javax.ws.rs.NotAuthorizedException
  - javax.ws.rs.NotFoundException
  - javax.ws.rs.NotSupportedException
  - javax.ws.rs.Priorities
```

This task can also be used to analyze other configurations in addition to `runtimeClasspath`, e.g.:

```gradle
checkClassUniqueness {
  configurations.add project.configurations.myConf
}
```

If you discover multiple jars on your classpath contain clashing classes, you should ideally try to fix them upstream and then depend on the fixed version.  If this is not feasible, you may be able to tell Gradle to [use a substituted dependency instead](https://docs.gradle.org/current/userguide/resolution_rules.html#sec:dependency_resolve_rules):

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

The plugin surfaces failures using JUnit XML which is rendered nicely by CircleCI, by

1. Storing JUnit test reports in `$CIRCLE_TEST_REPORTS/junit`
2. Storeing the HTML output of tests in `$CIRCLE_ARTIFACTS/junit`

## com.palantir.baseline-format

Adds a `./gradlew format` task which autoformats all Java files using [Spotless](https://github.com/diffplug/spotless). Roughly equivalent to:

```gradle
buildscript {
    dependencies {
        classpath 'com.diffplug.spotless:spotless-plugin-gradle:5.7.0'
    }
}

apply plugin: 'com.palantir.java-format'
apply plugin: 'com.diffplug.spotless'

spotless {
    java {
        target 'src/main/java/**/*.java', 'src/main/test/**/*.java'
        removeUnusedImports
        importOrder ''
        trimTrailingWhitespace
        indentWithSpaces 4
    }
    groovyGradle {
        greclipse().configFile("$rootDir/build/baseline-format/greclipse.properties")
    }
}
```

**Add `com.palantir.baseline-format.eclipse=true`** to your gradle.properties to format entire files with the Eclipse formatter. The Eclipse formatter can be run from IntelliJ using the [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) plugin.

To iterate on the eclipse.xml formatter config, you can import it into an instance of Eclipse, edit it through the preferences UI and then export it, or you can manually tune individual values by referring to the master list of [DefaultCodeFormatterConstants](https://github.com/eclipse/eclipse.jdt.core/blob/6a8cee1126829229d648db4ae0e5a6b70a5d4f13/org.eclipse.jdt.core/formatter/org/eclipse/jdt/core/formatter/DefaultCodeFormatterConstants.java) and [DefaultCodeFormatterOptions](https://github.com/eclipse/eclipse.jdt.core/blob/6a8cee1126829229d648db4ae0e5a6b70a5d4f13/org.eclipse.jdt.core/formatter/org/eclipse/jdt/internal/formatter/DefaultCodeFormatterOptions.java#L41-L95). Running `./gradlew :gradle-baseline-java:test -Drecreate=true` should update all the checked-in snapshot test cases.

**Add `com.palantir.baseline-format.gradle-files=true`** to your gradle.properties to format your own build.gradle files
(or alternatively run `./gradlew format -Pcom.palantir.baseline-format.gradle-files=true` to do a one-off run).


## com.palantir.baseline-reproducibility

This plugin is a shorthand for the following snippet, which opts-in to reproducible behaviour for all Gradle's Jar, Tar and Zip tasks. (Surprisingly, these tasks are not reproducible by default).

```gradle
tasks.withType(AbstractArchiveTask) {
    preserveFileTimestamps = false
    reproducibleFileOrder = true
}
```

It also warns if it detects usage of the [`nebula.info`](https://github.com/nebula-plugins/gradle-info-plugin) plugin which is known to violate the reproducibility of Jars by adding a 'Build-Date' entry to the MANIFEST.MF, which will be different on every run of `./gradlew jar`.

_Complete byte-for-byte reproducibility is desirable because it enables the [Gradle build cache](https://docs.gradle.org/4.10/userguide/build_cache.html) to be much more effective._


## com.palantir.baseline-exact-dependencies

This plugin adds two tasks to help users ensure they explicitly declare exactly the dependencies they need - nothing more and nothing less:

- `checkUnusedDependencies` - fails if a project pulls in a jar but never compiles against classes from it.  This is undesirable because it inflates published jars and distributions.
- `checkImplicitDependencies` - fails if source code relies on classes that only appear on the classpath transitively.  This is fragile because without a direct dependency on the relevant jar, a seemingly unrelated dependency upgrade could cause compilation to start failing.

Both of these tasks can be configured to ignore specific dependencies if this improves the signal-to-noise ratio. The following snippet illustrates the defaults that are baked into the plugin:

```gradle
checkUnusedDependencies {
    ignore 'javax.annotation', 'javax.annotation-api'
}

checkImplicitDependencies {
    ignore 'org.slf4j', 'slf4j-api'
}
```

## com.palantir.baseline-encoding

This plugin sets the encoding for JavaCompile tasks to `UTF-8`.

## com.palantir.baseline-testing

Configures some sensible defaults:

1. For debugging purposes:

    ```gradle
    tasks.withType(Test) {
        jvmArgs '-XX:+HeapDumpOnOutOfMemoryError', '-XX:+CrashOnOutOfMemoryError'
    }
    ```

    This ensures that if one of your tests fails with an OutOfMemoryError (OOM), you'll get a large hprof file in the relevant subdirectory which can be analyzed with heap analysis tools like Yourkit profiler, jvisualvm etc.

2. If Gradle detects you use JUnit 5 (i.e. you have a `testImplementation 'org:junit.jupiter:junit-jupiter'` dependency), it will automatically configure your `Test` tasks to run with `useJUnitPlatform()`, and configure all `@Test` methods to run in parallel by default.  Many other languages take this stance by default - if some tests rely on static state then you can mark them as non-parallel.

    See more here: https://junit.org/junit5/docs/current/user-guide/#writing-tests-parallel-execution

The plugin also adds a `checkJUnitDependencies` to make the migration to JUnit5 safer.  Specifically, it should prevent cases where the tests could silently not run due to misconfigured dependencies.

3. For repos that use 'snapshot' style testing, it's convenient to have a single command to accept the updated snapshots after a code change.
This plugin ensures that if you run tests with `./gradlew test -Drecreate=true`, the system property will be passed down to the running Java process (which can be detected with `Boolean.getBoolean("recreate")`).

## com.palantir.baseline-immutables

This plugin enables incremental compilation for the [Immutables](http://immutables.github.io/) annotation processor.

This plugin adds the `-Aimmutables.gradle.incremental` compiler arg to the compile Java task for any source set whose annotation processor configuration contains the Immutables annotation processor.

For more details, see the Immutables incremental compilation [tracking issue](https://github.com/immutables/immutables/issues/804).

## com.palantir.baseline-java-versions

This plugin allows consistent configuration of JDK versions via [Gradle Toolchains](https://docs.gradle.org/current/userguide/toolchains.html).
The plugin is currently used on an opt-in basis. To use it, apply the plugin and configure the default JDK versions in your root project (note that the plugin requires Gradle 7):

```gradle
// In the root build.gradle
apply plugin: 'com.palantir.baseline-java-versions'

javaVersions {
    javaCompiler = 25
    libraryTarget = 11
    distributionTarget = 17
    runtime = 21
}
```

The configurable fields of the `javaVersions` extension are:
* `javaCompiler`: (optional) The version of the Java compiler used. If not set, the appropriate `target` will be used. The `--release`, `--target` and `--source` compiler args are used to enable compiling code at a lower target than the compiler used. However, if `baseline-module-jvm-args`/`moduleJvmArgs` are used, `--release` will not be set as it is not compatible with exporting/opening system modules, meaning compilation will not fail if JDK APIs are used which are higher than the requested target.
* `libraryTarget`: (required) The Java version targeted for compilation of libraries that are published.
* `distributionTarget`: (optional) The Java version targeted for compilation of code used within distributions, but not published externally. Defaults to the `libraryTarget` version.
* `runtime`: (optional) Runtime Java version for testing and packaging distributions. Defaults to the `distributionTarget` version.

The configured Java versions are used as defaults for all projects.

You can run the `explainJavaVersions` task to both show what the `target` and `runtime` versions are for each subproject, and explain why:

```
$ ./gradle explainJavaVersions
> Task :subproject:explainJavaVersions
target  = 11
runtime = 17
Reason: considered a distribution because it doesn't have any publishing extensions defined
```

If a sub-project should use `libraryTarget` but is not considered a library (for example, because it is not published), you can explicitly indicate that it is a library:

```gradle
// In a sub-project's build.gradle
javaVersion {
    library()
}
```

A sub-project can also explicitly override the default Java versions, but doing so is discouraged:

```gradle
// In a sub-project's build.gradle
javaVersion {
    javaCompiler = 17
    target = 11
    runtime = 11
}
```

The optionally configurable fields of the `javaVersion` extension are:
* `javaCompiler`: The version of the Java compiler used. If not set, `target` will be used. See note in above `javaVersions` extension regarding `javaCompiler`.
* `target`: The target version used for compilation.
* `runtime`: The runtime version used for testing and distributions.

### Opting in to `--enable-preview` flag

As described in [JEP 12](https://openjdk.java.net/jeps/12), Java allows you to use incubating syntax features if you add the `--enable-preview` flag. Gradle requires you to add it in many places (including on JavaCompile, Javadoc tasks, as well as in production and on execution tasks like Test, JavaExec). The baseline-java-versions plugin provides a shorthand way of enabling this:

```gradle
// root build.gradle
apply plugin: 'com.palantir.baseline-java-versions'
javaVersions {
    libraryTarget = 11
    distributionTarget = '17_PREVIEW'
    runtime = '17_PREVIEW'
}

// shorthand for configuring all the tasks individually, e.g.
tasks.withType(JavaCompile) {
    options.compilerArgs += "--enable-preview"
}
tasks.withType(Test) {
    jvmArgs += "--enable-preview"
}
tasks.withType(JavaExec) {
    jvmArgs += "--enable-preview"
}
```

In the example above, the `Baseline-Enable-Preview: 17` attribute will be embedded in the resultant Jar's `META-INF/MANIFEST.MF` file. To see for yourself, run:

```
$ unzip -p /path/to/your-project-1.2.3.jar META-INF/MANIFEST.MF
```

_Note, this plugin should be used with **caution** because preview features may change or be removed, which might make upgrading to a new Java version harder._
