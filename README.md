<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-baseline"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# Baseline Java code quality plugins
[![CircleCI Build Status](https://circleci.com/gh/palantir/gradle-baseline/tree/develop.svg?style=shield)](https://circleci.com/gh/palantir/gradle-baseline)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/palantir/baseline/com.palantir.baseline.gradle.plugin/maven-metadata.xml.svg?label=plugin&logo=gradle)](https://plugins.gradle.org/plugin/com.palantir.baseline)

_Baseline is a family of Gradle plugins for configuring Java projects with sensible defaults for code-style, static analysis, dependency versioning, CircleCI and IntelliJ IDEA/Eclipse integration._

| Plugin                                         | Description            |
|------------------------------------------------|------------------------|
| `com.palantir.baseline-idea`                   | Configures [Intellij IDEA](https://www.jetbrains.com/idea/) with code style and copyright headers
| `com.palantir.baseline-eclipse`                | Configures [Eclipse](https://www.eclipse.org/downloads/) with code style and copyright headers
| `com.palantir.baseline-error-prone`            | Static analysis for your Java code using Google's [error-prone](http://errorprone.info/).
| `com.palantir.baseline-checkstyle`             | Enforces consistent Java formatting using [checkstyle](http://checkstyle.sourceforge.io/)
| `com.palantir.baseline-format`                 | Formats your java files to comply with checkstyle
| `com.palantir.baseline-scalastyle`             | Enforces formatting using [scalastyle](https://github.com/scalastyle/scalastyle)
| `com.palantir.baseline-class-uniqueness`       | Analyses your classpath to ensure no fully-qualified class is defined more than once.
| `com.palantir.baseline-circleci`               | [CircleCI](https://circleci.com/) integration using `$CIRCLE_ARTIFACTS` and `$CIRCLE_TEST_REPORTS` dirs
| `com.palantir.baseline-config`                 | Config files for the above plugins
| `com.palantir.baseline-reproducibility`        | Sensible defaults to ensure Jar, Tar and Zip tasks can be reproduced
| `com.palantir.baseline-exact-dependencies`     | Ensures projects explicitly declare all the dependencies they rely on, no more and no less
| `com.palantir.baseline-encoding`               | Ensures projects use the UTF-8 encoding in compile tasks.
| `com.palantir.baseline-release-compatibility`  | Ensures projects targeting older JREs only compile against classes and methods available in those JREs.
| `com.palantir.baseline-testing`                | Configures test tasks to dump heap dumps (hprof files) for convenient debugging
| `com.palantir.baseline-immutables`             | Enables incremental compilation for the [Immutables](http://immutables.github.io/) annotation processor.
| `com.palantir.baseline-java-versions`          | Configures JDK versions in a consistent way via Gradle Toolchains.
| `com.palantir.baseline-prefer-project-modules` | Configures Gradle to prefer project modules over external modules on dependency resolution per default.

See also the [Baseline Java Style Guide and Best Practices](./docs).


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
The `com.palantir.baseline-error-prone` plugin brings in the `net.ltgt.errorprone-javacplugin` plugin. We recommend applying the `org.inferred.processors` plugin 1.3.0+ in order to avoid `error: plug-in not found: ErrorProne`. The minimal setup is as follows:

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
tasks.withType(JavaCompile).configureEach(new Action<Task>() {
    public void execute(Task task) {
        task.options.errorprone.disable 'Slf4jLogsafeArgs'
    }
})
```

More information on error-prone severity handling can be found at [errorprone.info/docs/flags](http://errorprone.info/docs/flags).

#### Baseline error-prone checks
Baseline configures the following checks in addition to the [error-prone's out-of-the-box
checks](https://errorprone.info):

- `DangerousParallelStreamUsage`: Discourage the use of Java parallel streams.
- `Slf4jConstantLogMessage`: Allow only compile-time constant slf4j log message strings.
- `Slf4jLevelCheck`: Slf4j level checks (`if (log.isInfoEnabled()) {`) must match the most severe level in the containing block.
- `Slf4jLogsafeArgs`: Allow only com.palantir.logsafe.Arg types as parameter inputs to slf4j log messages. More information on
Safe Logging can be found at [github.com/palantir/safe-logging](https://github.com/palantir/safe-logging).
- `PreferCollectionTransform`: Prefer Guava's Lists.transform or Collections2.transform instead of Iterables.transform when first argument's declared type is a List or Collection type for performance reasons.
- `PreferListsPartition`: Prefer Guava's `Lists.partition(List, int)` instead of `Iterables.partition(Iterable, int)` when first argument's declared type is a list for performance reasons.
- `PreferSafeLoggableExceptions`: Users should throw `SafeRuntimeException` instead of `RuntimeException` so that messages will not be needlessly redacted when logs are collected:
    ```diff
    -throw new RuntimeException("explanation", e); // this message will be redacted when logs are collected
    +throw new SafeRuntimeException("explanation", e); // this message will be preserved (allowing easier debugging)
    ```
- `PreferSafeLoggingPreconditions`: Users should use the safe-logging versions of Precondition checks for standardization when there is equivalent functionality
    ```diff
    -com.google.common.base.Preconditions.checkNotNull(variable, "message");
    +com.palantir.logsafe.Preconditions.checkNotNull(variable, "message"); // equivalent functionality is available in the safe-logging variant
    ```
- `ShutdownHook`: Applications should not use `Runtime#addShutdownHook`.
- `GradleCacheableTaskAction`: Gradle plugins should not call `Task.doFirst` or `Task.doLast` with a lambda, as that is not cacheable. See [gradle/gradle#5510](https://github.com/gradle/gradle/issues/5510) for more details.
- `PreferBuiltInConcurrentKeySet`: Discourage relying on Guava's `com.google.common.collect.Sets.newConcurrentHashSet()`, when Java's `java.util.concurrent.ConcurrentHashMap.newKeySet()` serves the same purpose.
- `JUnit5RuleUsage`: Prevent accidental usage of `org.junit.Rule`/`org.junit.ClassRule` within Junit5 tests
- `DangerousCompletableFutureUsage`: Disallow CompletableFuture asynchronous operations without an Executor.
- `NonComparableStreamSort`: Stream.sorted() should only be called on streams of Comparable types.
- `DangerousStringInternUsage`: Disallow String.intern() invocations in favor of more predictable, scalable alternatives.
- `OptionalOrElseThrowThrows`: Optional.orElseThrow argument must return an exception, not throw one.
- `OptionalOrElseGetValue`: Prefer `Optional.orElse(value)` over `Optional.orElseGet(() -> value)` for trivial expressions.
- `LambdaMethodReference`: Lambda should use a method reference.
- `SafeLoggingExceptionMessageFormat`: SafeLoggable exceptions do not interpolate parameters.
- `StrictUnusedVariable`: Functions shouldn't have unused parameters.
- `StringBuilderConstantParameters`: StringBuilder with a constant number of parameters should be replaced by simple concatenation.
- `JUnit5SuiteMisuse`: When migrating from JUnit4 -> JUnit5, classes annotated with `@RunWith(Suite.class)` are dangerous because if they reference any JUnit5 test classes, these tests will silently not run!
- `ThrowError`: Prefer throwing a RuntimeException rather than Error.
- `DnsLookup`: Calling `new InetSocketAddress(host, port)` results in a DNS lookup which prevents the address from following DNS changes.
- `ReverseDnsLookup`: Calling address.getHostName may result in an unexpected DNS lookup.
- `ReadReturnValueIgnored`: The result of a read call must be checked to know if EOF has been reached or the expected number of bytes have been consumed.
- `FinalClass`: A class should be declared final if all of its constructors are private.
- `RedundantModifier`: Avoid using redundant modifiers.
- `StrictCollectionIncompatibleType`: Likely programming error due to using the wrong type in a method that accepts Object.
- `InvocationHandlerDelegation`: InvocationHandlers which delegate to another object must catch and unwrap InvocationTargetException.
- `Slf4jThrowable`: Slf4j loggers require throwables to be the last parameter otherwise a stack trace is not produced.
- `JooqResultStreamLeak`: Autocloseable streams and cursors from jOOQ results should be obtained in a try-with-resources statement.
- `StreamOfEmpty`: Stream.of() should be replaced with Stream.empty() to avoid unnecessary varargs allocation.
- `RedundantMethodReference`: Redundant method reference to the same type.
- `ExceptionSpecificity`: Prefer more specific catch types than Exception and Throwable.
- `ThrowSpecificity`: Prefer to declare more specific `throws` types than Exception and Throwable.
- `UnsafeGaugeRegistration`: Use TaggedMetricRegistry.registerWithReplacement over TaggedMetricRegistry.gauge.
- `CollectionStreamForEach`: Collection.forEach is more efficient than Collection.stream().forEach.
- `LoggerEnclosingClass`: Loggers created using getLogger(Class<?>) must reference their enclosing class.
- `UnnecessaryLambdaArgumentParentheses`: Lambdas with a single parameter do not require argument parentheses.
- `RawTypes`: Avoid raw types; add appropriate type parameters if possible.
- `VisibleForTestingPackagePrivate`: `@VisibleForTesting` members should be package-private.
- `OptionalFlatMapOfNullable`: Optional.map functions may return null to safely produce an empty result.
- `ExtendsErrorOrThrowable`: Avoid extending Error (or subclasses of it) or Throwable directly.
- `ImmutablesStyle`: Disallow the use of inline immutables style annotations to avoid forcing compile dependencies on consumers.
- `ImmutablesReferenceEquality`: Comparison of Immutables value using reference equality instead of value equality.
- `TooManyArguments`: Prefer Interface that take few arguments rather than many.
- `ObjectsHashCodeUnnecessaryVarargs`: java.util.Objects.hash(non-varargs) should be replaced with java.util.Objects.hashCode(value) to avoid unnecessary varargs array allocations.
- `PreferStaticLoggers`: Prefer static loggers over instance loggers.
- `LogsafeArgName`: Prevent certain named arguments as being logged as safe. Specify unsafe argument names using `LogsafeArgName:UnsafeArgNames` errorProne flag.
- `ImplicitPublicBuilderConstructor`: Prevent builders from unintentionally leaking public constructors.
- `ImmutablesBuilderMissingInitialization`: Prevent building Immutables.org builders when not all fields have been populated.
- `UnnecessarilyQualified`: Types should not be qualified if they are also imported.
- `DeprecatedGuavaObjects`: `com.google.common.base.Objects` has been obviated by `java.util.Objects`.
- `JavaTimeSystemDefaultTimeZone`: Avoid using the system default time zone.
- `IncubatingMethod`: Prevents calling Conjure incubating APIs unless you explicitly opt-out of the check on a per-use or per-project basis.
- `CompileTimeConstantViolatesLiskovSubstitution`: Requires consistent application of the `@CompileTimeConstant` annotation to resolve inconsistent validation based on the reference type on which the met is invoked.
- `ClassInitializationDeadlock`: Detect type structures which can cause deadlocks initializing classes.
- `ConsistentLoggerName`: Ensure Loggers are named consistently.
- `PreferImmutableStreamExCollections`: It's common to use toMap/toSet/toList() as the terminal operation on a stream, but would be extremely surprising to rely on the mutability of these collections. Prefer `toImmutableMap`, `toImmutableSet` and `toImmutableList`. (If the performance overhead of a stream is already acceptable, then the `UnmodifiableFoo` wrapper is likely tolerable).
- `DangerousIdentityKey`: Key type does not override equals() and hashCode, so comparisons will be done on reference equality only.
- `ConsistentOverrides`: Ensure values are bound to the correct variables when overriding methods
- `FilterOutputStreamSlowMultibyteWrite`: Subclasses of FilterOutputStream should provide a more efficient implementation of `write(byte[], int, int)` to avoid slow writes.
- `BugCheckerAutoService`: Concrete BugChecker implementations should be annotated `@AutoService(BugChecker.class)` for auto registration with error-prone.

### Programmatic Application

There exist a number of programmatic code modifications available via [refaster](https://errorprone.info/docs/refaster). You can run these on your code to apply some refactorings automatically:

```bash
./gradlew compileJava compileTestJava -PrefasterApply -PerrorProneApply
```

You may apply specific error-prone refactors including those which are not enabled by default by providing a comma
delimited list of check names to the `-PerrorProneApply` option.

```bash
./gradlew compileJava compileTestJava -PerrorProneApply=ThrowSpecificity
```

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

To disable certain checks for an entire file, apply [custom suppressions](http://checkstyle.sourceforge.io/config.html)
in `.baseline/checkstyle/custom-suppressions.xml`. Avoid adding suppressions to the autogenerated `.baseline/checkstyle/checkstyle-suppressions.xml`,
as that file will be overridden on updates.

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

## com.palantir.baseline-release-compatibility

This plugin adds the `--release <number>` flag to JavaCompile tasks (when the compiler [supports it](https://openjdk.java.net/jeps/247)), so that published jars will only use methods available in the target JRE.  Relying on `sourceCompatibility = 1.8` and `targetCompatibility = 1.8` is insufficient because you run the risk of using method that have been added in newer JREs, e.g. `Optional#isEmpty`.

This plugin may become redundant if this functionality is implemented upstream [in Gradle](https://github.com/gradle/gradle/issues/2510).

## com.palantir.baseline-testing

Configures some sensible defaults:

1. For debugging purposes:

    ```gradle
    tasks.withType(Test) {
        jvmArgs '-XX:+HeapDumpOnOutOfMemoryError', '-XX:+CrashOnOutOfMemoryError'
    }
    ```

    This ensures that if one of your tests fails with an OutOfMemoryError (OOM), you'll get a large hprof file in the relevant subdirectory which can be analyzed with Eclipse Memory Analyzer Tool, Yourkit profiler, jvisualvm etc.

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
    libraryTarget = 11
    distributionTarget = 15
    runtime = 17
}
```

The configurable fields of the `javaVersions` extension are:
* `libraryTarget`: (required) The Java version used for compilation of libraries that are published.
* `distributionTarget`: (optional) The Java version used for compilation of code used within distributions, but not published externally. Defaults to the `libraryTarget` version.
* `runtime`: (optional) Runtime Java version for testing and packaging distributions. Defaults to the `distributionTarget` version.

The configured Java versions are used as defaults for all projects. Optionally, you can override the defaults in a sub-project, but it is recommended to avoid doing so:

```gradle
// In a sub-project's build.gradle
javaVersion {
    target = 11
    runtime = 11
}
```

The optionally configurable fields of the `javaVersion` extension are `target`, for setting the target version used for compilation and `runtime`, for setting the runtime version used for testing and distributions.

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
