# Java Coding Guidelines

This document contains guidelines and best practices for developing
software in the Java programming language and covers the following
topics:

- [Java Coding Guidelines](#java-coding-guidelines)
  - [Miscellaneous](#miscellaneous)
    - [Minimize mutability](#minimize-mutability)
      - [tip](#tip)
    - [Use ternary operators sparingly](#use-ternary-operators-sparingly)
    - [Comments](#comments)
    - [Check parameters for validity](#check-parameters-for-validity)
    - [Be aware of the performance of string concatenation](#be-aware-of-the-performance-of-string-concatenation)
    - [Reflection and classpath scanning](#reflection-and-classpath-scanning)
    - [Return values and errors](#return-values-and-errors)
    - [Import the canonical package](#import-the-canonical-package)
    - [Avoid nested blocks](#avoid-nested-blocks)
    - [Methods and functions: focused, crisp, concise](#methods-and-functions-focused-crisp-concise)
    - [Never override Object\#finalize or Object\#clone](#never-override-objectfinalize-or-objectclone)
    - [Override `Object#equals` consistently](#override-objectequals-consistently)
    - [Static members: qualified using class](#static-members-qualified-using-class)
    - [Inner assignments: Not used](#inner-assignments-not-used)
    - [For-loop control variables: never modified](#for-loop-control-variables-never-modified)
    - [String equality: use String\#equals](#string-equality-use-stringequals)
    - [Reduce Cyclomatic Complexity](#reduce-cyclomatic-complexity)
    - [Never instantiate primitive types](#never-instantiate-primitive-types)
    - [Deprecate per annotation and Javadoc](#deprecate-per-annotation-and-javadoc)
    - [Avoid Generics clutter where possible](#avoid-generics-clutter-where-possible)
    - [Keep Boolean expressions simple](#keep-boolean-expressions-simple)
    - [Durations: measured by longs or complex types, not by ints](#durations-measured-by-longs-or-complex-types-not-by-ints)
    - [Avoid new HashMap(int), use Maps.newHashMapWithExpectedSize(int)](#avoid-new-HashMap(int))
  - [APIs and Interfaces](#apis-and-interfaces)
    - [Indicate failure consistently](#indicate-failure-consistently)
    - [Return empty arrays and collections, not null](#return-empty-arrays-and-collections-not-null)
    - [Method overloading: allowed, but be reasonable](#method-overloading-allowed-but-be-reasonable)
    - [Private constructors](#private-constructors)
    - [Final variables and parameters](#final-variables-and-parameters)
    - [Avoid redundant modifiers](#avoid-redundant-modifiers)
    - [Avoid shadowing](#avoid-shadowing)
    - [Limit coupling on concrete classes](#limit-coupling-on-concrete-classes)
    - [Make interfaces and signatures concise and specific](#make-interfaces-and-signatures-concise-and-specific)
  - [Libraries](#libraries)
  - [Design Patterns](#design-patterns)
    - [Use the Builder Pattern](#use-the-builder-pattern)
    - [Use the Visitor Pattern](#use-the-visitor-pattern)
  - [Inheritance](#inheritance)
    - [Always use @Override](#always-use-override)
    - [Favor composition over inheritance](#favor-composition-over-inheritance)
    - [Design for extension](#design-for-extension)
    - [Prefer interfaces over abstract classes](#prefer-interfaces-over-abstract-classes)
    - [Prefer class hierarchies to tagged classes](#prefer-class-hierarchies-to-tagged-classes)
    - [Bounded wildcards (PECS)](#bounded-wildcards-pecs)
  - [Enums](#enums)
    - [Use enums instead of int constants](#use-enums-instead-of-int-constants)
    - [Use instance fields instead of ordinals](#use-instance-fields-instead-of-ordinals)
    - [Never miss a case](#never-miss-a-case)
  - [Exception Handling](#exception-handling)
    - [Checked and unchecked exceptions](#checked-and-unchecked-exceptions)
    - [Don't ignore exceptions](#dont-ignore-exceptions)
    - [Throwable, Error, RuntimeException: Not declared](#throwable-error-runtimeexception-not-declared)
    - [Exceptions: always immutable](#exceptions-always-immutable)
    - [Try/catch blocks: never nested](#trycatch-blocks-never-nested)
  - [Log Levels](#log-levels)
  - [Concurrency](#concurrency)
    - [Before you start: Read the books](#before-you-start-read-the-books)
    - [Use higher-level abstracttions](#use-higher-level-abstracttions)
    - [Prefer simple synchronization schemes](#prefer-simple-synchronization-schemes)
    - [Always ensure your synchronization scheme is correct](#always-ensure-your-synchronization-scheme-is-correct)
    - [Avoid leaking 'this' from constructors](#avoid-leaking-this-from-constructors)
  - [Testing](#testing)
    - [Use appropriate assertion methods](#use-appropriate-assertion-methods)
    - [Avoid assertNotNull](#avoid-assertnotnull)
  - [Dependency Injection](#dependency-injection)
    - [Restrict constructor parameters](#restrict-constructor-parameters)
    - [Avoid doing work in constructors](#avoid-doing-work-in-constructors)
    - [Ensure objects are fully initialized after construction](#ensure-objects-are-fully-initialized-after-construction)
    - [Static fields should be immutable and final](#static-fields-should-be-immutable-and-final)
    - [Avoid non-trivial static methods](#avoid-non-trivial-static-methods)
    - [Refer to objects by their interfaces](#refer-to-objects-by-their-interfaces)
    - [Prefer a single constructor](#prefer-a-single-constructor)
    - [Dependency injection frameworks](#dependency-injection-frameworks)

**Further reading**

- Effective Java, 2nd Edition. by Joshua Bloch (copies exist in
    all offices)
- Java Concurrency in Practice (copies exist in all offices)
- [Writing Testable Code](http://misko.hevery.com/code-reviewers-guide/)
- [How to Design a GoodAPI and Why it Matters (Bloch)](http://fwdinnovations.net/whitepaper/APIDesign.pdf)

## Miscellaneous

### Minimize mutability

Make every field immutable whenever possible. Make every class immutable
where possible by declaring all of its fields `final` and never
returning them in a way that allows them to be changed. For example,
when you implement a method that returns a list member variable, try to
make that member variable an ImmutableList, or otherwise return a copy
or an immutable view. Immutability simplifies debugging and
multi-threading.

Fields should be private unless they are static final, immutable, or
annotated with `@VisibleForTesting` or `@Rule`.

#### tip

Some teams use the `@Immutable` annotation in order to document that a
class is immutable and can be safely used in multi-threaded applications.
Read *Java Concurrency in Practice* in order to understand when a
class is immutable.

See *Effective Java, 2nd Edition, Item 15*

### Use ternary operators sparingly

Ternary operators can often make code hard to follow. Use them in only
the simplest of cases.

### Comments

Long comments should be full sentences. They should start with a capital
letter and end with a punctuation mark.

Naturally, blocks of code whose purpose isn't clear should be commented.
In almost all cases, each class and each method should have at least a
short (Javadoc) comment describing its functionality. In cases where
preconditions are expressed at the beginning of a method, these
preconditions may be omitted from method comments. Comments are not
required for trivial methods (getters, setters, etc.).

Commented-out code is generally banned; its purpose tends to be
confusing to the uninitiated and it is usually never removed or
consolidated.

### Check parameters for validity

Preconditions on parameters should be validated at the beginning of
methods and constructors. Validation checks document method APIs and
clarify program logic by establishing constraints on possible values and
states of variables and fields.

The following examples provide guidance on authoring appropriate
validation checks with minimal syntactic and run-time overhead.

**Avoid manual validation code**. Use Guava `Preconditions` or Apache
Commons Lang `Validate` instead of manual verification.

``` java
// BAD. Don't do this.
public ManuallyValidated(String name, String uri) {
    this.name = name;
    this.uri = uri;
    if (null == this.name || null == this.uri) {
        throw new IllegalArgumentException("Null name or uri");
    }

    if (this.name.length() == 0) {
        throw new IllegalArgumentException("Name may not be empty");
    }
}

// Good.
public LibraryValidated(String name, String uri) {
    this.name = Validate.notEmpty(name, "Name may not be empty.");
    this.uri = Validate.notNull(uri, "URI may not be null.");
}
```

**Prefer validation methods that return the validated parameter**. Both
Guava and Apache Commons provide validation primitives that return the
validated parameter; this reduces the syntactic overhead of validation:

``` java
// BAD. Don't do this.
public SyntaxOverhead(String name, String uri) {
    Validate.notEmpty(name, "Name may not be empty.");
    Validate.notNull(uri, "URI may not be null.");

    this.name = name;
    this.uri = uri;
}

// Good.
public Concise(String name, String uri) {
    this.name = Validate.notEmpty(name, "Name may not be empty.");
    this.uri = Validate.notNull(uri, "URI may not be null.");
}
```

**Library choice and consistency**. We prefer Guava over Apache Commons.
However, when required checks are not available in Guava (for example,
checking that a string is not null and not empty), teams need to trade
off validation consistency and brevity:

- If all checks are available in Guava, use Guava.
- Otherwise, bias towards consistency (do not mix Guava and Apache
    Commons), brevity (by using checks that return the validated
    parameter), and symmetry between validation and assignment.

Some projects consciously avoid a dependency on Guava in order to steer
clear of version conflicts. In such cases, use Apache Commons.

``` java
// BAD. Don't do this.
public AsymmetryBetweenValidationAndAssignment(String name, String uri) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(name, "Name may not be empty."));
    this.name = name;
    this.uri = Preconditions.checkNotNull(uri, "URI may not be null.");
}

// BAD. Don't do this.
public MixedLibraryUse(String name, String uri) {
    this.name = Validate.notEmpty(name, "Name may not be empty");
    this.uri = Preconditions.checkNotNull(uri, "URI may not be null.");
}
```

**Validation must be cheap**. It is authoritative that checks are cheap
(i.e., roughly constant-time). There are some pitfalls, for example:

``` java
// BAD. Don't do this.
void myMethod(Set<String> set, String value) {
    // set.toString() is O(n) and gets computed even if the check succeeds.
    Preconditions.checkState(set.contains(myValue), "Set does not contain " + value+ ": " + set.toString());
}

// BAD. Don't do this.
void myMethod(ConcurrentLinkedQueue<String> strings, int n) {
    // ConcurrentLinkedQueue#size is O(n) and thus potentially very expensive.
    Preconditions.checkState(strings.size() >= n, "Expected at least " + n + " strings, found: " + strings.size());
    ...
}
```

**Failed validations should provide appropriate context**. Consider the
error message and stacktrace produced by validation checks:

(1) Does it provide sufficient context to be useful for debugging? For
    example, `Validate#notNull` and `Preconditions#checkNotNull` should
    always indicate the name of the validated variable; similarly,
    failed non-trivial preconditions can be easier to understand for
    users when the failure mode is explained:

``` java
// BAD. Don't do this.
Validate.notNull(username); // Yields NullPointerException that does not convey which variable was null.
// User has to look at source code in order to understand password requirements:
Preconditions.checkArgument(Pattern.matches(PASSWORD_REGEX, password));

// Good.
Validate.notNull(username, "username may not be null");
Preconditions.checkArgument(Pattern.matches(PASSWORD_REGEX, password),
    "Provided password does not match regular expression: " + PASSWORD_REGEX);
```

(2) Does it leak potentially sensitive information such as incorrect
    passwords or classified information?

``` java
// BAD. Don't do this.
Preconditions.checkArgument(Pattern.matches(PASSWORD_REGEX, password),
    "Provided password (" + password + ") does not match regular expression: " + PASSWORD_REGEX);

// Good.
Preconditions.checkArgument(Pattern.matches(PASSWORD_REGEX, password),
    "Provided password does not match regular expression: " + PASSWORD_REGEX);
```

See *Effective Java, 2nd Edition, Item 38*

### Prefer explicit `if` and `throw` or `Precondition` over `assert`

An `assert` statement is only executed if the JVM is started with
`--enableassertions`. This option is typically used during testing, but not when
running in production, which means that the semantics will differ between
test and production environments. For this reason it's preferable to use
explicit checks such as `if` and `throw` or `Precondition`.

### Be aware of the performance of string concatenation

Calculating

``` java
for (i = 0; i < N; i++) {
    str += foo(i);
}
```

is `O(N^2)` in the length of the final string. Instead, use

``` java
StringBuilder sb = new StringBuilder();
for (i = 0; i < N; i++) {
    sb.append(foo(i));
}
```

which is `O(N)`. This can be a big deal in log messages that are
frequently hit, or for very long strings. Do not use this for simple
combinations of strings you know to be small – then it just decreases
readability.

See *Effective Java, 2nd Edition, Item 51*

### Reflection and classpath scanning

Use reflection only as a last resort, prefer factory interfaces or
`ServiceLoader` instead. Usually prefer `getCanonicalName()` over
`getName()` as it produces more readable output). Never use
`Class.toString()` in log or debug messages.

### Return values and errors

Complex methods usually have failure and success modes. Handle failure
cases first (by returning a failure value or throwing an exception), and
success cases last; try not to intertwine success and error cases. A
good indicator is the occurrence of return statements within a function.
A good function should return error or base cases early, then perform
the core method logic, then return the result at the end.

``` java
// BAD. Don't do this.
public boolean complexMethod(int leftIndex, int rightIndex, List<Integer> values) {

    int result = 0;
    int numSeen = 0;
    for (...) {
        numSeen += 1;
        if (...) {
            ...
            result += ...;
        }
    }

    if (numSeen % 2 != 0) {
        throw new Exception();
    }

    return result;
}

// Good.
public boolean complexMethod(int leftIndex, int rightIndex, List<Integer> values) {
    // Checking parameters is the first potential error case.
    Preconditions.checkPositionIndexes(leftIndex, rightIndex, values.size());

    // Now we can validate some more complicated assumptions.
    Preconditions.checkArgument(values.size() % 2 == 0, "This method only supports an even number of values.");

    // Now perform the computation; it is guaranteed to succeed.
    int result = 0;
    for (...) {
        if (...) {
            ...
            result += ...;
        }
    }

    return result;
}
```

One good heuristic is to keep the structure of return statements as
symmetric as possible, for example:

``` java
// BAD. Don't do this.
int method(int value) {
    if (value >= 3) {
        return -1;
    }

    int result = value;
    switch(value) {
        case 0:
            return 17;
        case 1:
            result += value - 1;
            break;
        default:
            return 5;
    }

    return result -1;
}

// Good.
int method(int value) {
    switch(value) {
        case 0:
            return 17;
        case 1:
            return 2 * (value - 1);
        case 2:
            return 5;

        default:
            return -1;
    }
}
```

A common pattern in methods that iterate over data structures in order
to find a particular return value is the following:

``` java
ComplexValue retrieve(DataStructure values, Predicate condition) {
    for (ComplexValue v : values) {
        if (condition.matches(v)) {
            return v;
        }
    }

    return null;  // We only reach this point if no value was found that matches the condition.
}
```

This pattern is OK in simple cases as the above, but is discouraged when
the logic becomes more complicated.

**note**: If you find yourself using the above pattern often, then your data
structure may not be a good fit.

### Import the canonical package

Some (mostly outdated) open source libraries ship explicit, renamed
copies of other, independent libraries. For example, certain versions of
Elasticsearch contain copies of Guava and Joda-Time. Never import these
repackaged libraries.

### Avoid nested blocks

Nested blocks such as

``` java
public void guessTheOutput() {
    int whichIsWhich = 0;
    {
        int whichIsWhich = 2;
    }
    System.out.println("value = " + whichIsWhich);
}
```

or

``` java
// if (conditionThatIsNotUsedAnyLonger)
{
    System.out.println("unconditional");
}
```

are discouraged as they are usually leftovers from debugging sessions
and can introduce bugs.

A case in a switch statement does not implicitly form a block. Thus to
be able to introduce local variables that have case scope it is
necessary to open a nested block. This is permitted:

``` java
switch (errorCode) {
    case 1: {
        int response = 200;
        // ...
        break;
    }
    case 2: {
        int response = 404;
        // ...
        break;
    }
}
```

### Methods and functions: focused, crisp, concise

Small and focused functions are easy to understand, testable, and
reusable. Their effect and purpose can be summarized into a crisp
function name. Indicators for overly long or complicated methods are:

- **Scrolling**. The function code does not fit on the screen
    without scrolling.
- **Unclear scope**. The function name does not describe what the
    method does.
- **Nesting**. Looping and control statements are overly nested.
- **Copy&paste**. Parts of the functionality are duplicated in other
    functions or classes.

### Never override Object\#finalize or Object\#clone

(modified from [Google Java Style
Guide](https://github.com/google/styleguide), section 6.4)

It is extremely rare to override `Object#finalize` or `Object#clone`.

**tip**
Don't do it. If you absolutely must, first read and understand Effective Java Item 7,
"Avoid Finalizers," very carefully, and then don't do it.

### Override `Object#equals` consistently

When overriding `Object#equals(MyObject o)` for some class `MyObject`,
then also override `Object#equals(Object o)`. Not doing so can yield
unexpected behaviour.

### Static members: qualified using class

([Google Java Style Guide](https://github.com/google/styleguide),
section 6.3)

When a reference to a static class member must be qualified, it is
qualified with that class's name, not with a reference or expression of
that class's type.

``` java
Foo aFoo = ...;
Foo.aStaticMethod(); // good
aFoo.aStaticMethod(); // bad
somethingThatYieldsAFoo().aStaticMethod(); // very bad
```

### Inner assignments: Not used

With the exception of for loops (`for (int i = 0; ...)`), inner
assignments of variables are discouraged.

``` java
// BAD. Don't do this.
String s = Integer.toString(i = 2);
myFunction(foo = "bar");
```

### For-loop control variables: never modified

The loop variable in a simple for-loop is never modified inside the
loop.

``` java
// BAD. Don't do this.
for (int i = 0; i <= 10; ++i) {
    i += 1;
    print(i);
}
```

### String equality: use String\#equals

String equality is always determined via `s1.equals(s2)` rather than
`s1 == s2`.

### Reduce Cyclomatic Complexity

Code with Cyclomatic Complexity above 10 will be rejected by
Checkstyle's CyclomaticComplexity check. Its documentation:

The complexity is measured by the number of if, while, do, for, ?:,
catch, switch, case statements, and operators && and || (plus one) in
the body of a constructor, method, static initializer, or instance
initializer. It is a measure of the minimum number of possible paths
through the source and therefore the number of required tests. Generally
1-4 is considered good, 5-7 OK, 8-10 consider re-factoring, and 11+
re-factor now!

### Never instantiate primitive types

For performance reasons, objects corresponding to primitive types
(Boolean, Byte, Character, Double, Integer, Long) should never be
directly instantiated.

### Deprecate per annotation and Javadoc

When deprecating a method or class, add both the `java.lang.Deprecated`
annotation and -- if Javadoc is present -- the Javadoc `@deprecated`
tag.

### Avoid Generics clutter where possible

Since Java 7, Generic types are automatically inferred in many places
where they previously had to be made explicit. Prefer less syntactic
clutter while maintaining type satefy.

``` java
// BAD. Don't do this.
Map<String, String> map = new HashMap<String, String>(); // Clutter.
Map<String, String> map = Collections.EMPTY_MAP; // Untyped.

// Good.
Map<String, String> map = new HashMap<>();
Map<String, String> map = Collections.emptyMap();
```

Factory methods often make the code shorter and thus easier to read:

``` java
// BAD. Don't do this.
ImmutableList.Builder<String> l = new ImmutableList.Builder<>();

// Good.
ImmutableList.Builder<String> l = ImmutableList.builder();
```

Guava collection factory methods such as `Maps.newHashMap`,
`Lists.newArrayList` et al, should however be considered deprecated
and should not be used.

``` java
// BAD. Don't do this.
List<String> list = Lists.newArrayList();
Map<String, String> map = Maps.newHashMap();

// Good.
List<String> list = new ArrayList<>();
Map<String, String> map = new HashMap<>();
```

### Keep Boolean expressions simple

Overly complicated Boolean expressions are discouraged. Redundant
Boolean sub-expressions should be simplified.

``` java
// BAD. Don't do this.
if (b == true) { ... }
if (b || true) { foo(); }  // Tautology

if (isBar()) {
    return true;
} else {
    return false;
}

// Good.
if (b) { ... }
foo();

return isBar();
```

### Durations: measured by longs or complex types, not by ints

The maximum number of days representable by a positive (signed) int
measuring milliseconds is about 24 (`2^31 / 60^2 / 24`). Any time
interval that may feasibly take values on the order of hours or days
must thus be represented by `long` types, or by appropriate
non-primitive types such as Java8/Joda-Time
[Duration](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html).

### Avoid new HashMap(int)

Avoid `new HashMap(int)` and `new HashSet(int)`, use `Maps#newHashMapWithExpectedSize(int)` and `Sets#newHashSetWithExpectedSize(int)` instead.

The behavior of `new HashMap(int)`/`new HashSet(int)` is misleading -- the parameter represents an
internal size rather than an ability to hold that number of elements.  If a HashMap
with capacity K receives K elements, it will increase its capacity to 2*K along the way.
This is because HashMap doubles its internal storage by 2 once it reaches 75% capacity.

The Guava static methods `Maps.newHashMapWithExpectedSize(int)` and `Sets#newHashSetWithExpectedSize(int)` creates a
HashMap which will not resize if provided the given number of elements.

## APIs and Interfaces

### Indicate failure consistently

There are many ways for a method to return or indicate failure: return
null, throw an exception, return an empty collection, return
Optional.empty(), etc pp. Any of these has its merits, but the
important thing is to be consistent, at least within the same method and
class.

### Return empty arrays and collections, not null

Returning null leads to NullPointerExceptions, and most code that
handles collections will naturally handle empty collections, so just
return empty when there are no values.

See *Effective Java, 2nd Edition, Item 43*

### Method overloading: allowed, but be reasonable

Overloading methods is useful in certain cases, such as providing
default arguments. However, they can convolute the code base when taken
to an extreme. In general, methods should not be overloaded more than
once or twice.

### Private constructors

A class should be declared `final` if all of its constructors are
private.

Utility classes -- i.e., classes all of whose methods and fields are
static -- have a private, empty, zero-argument constructor.

### Final variables and parameters

Use `final` sparingly for variables and parameters as it impacts
readability. Instead, enforce that parameters are never re-assigned
through static checks such as Checkstyle.

``` java
// BAD. Don't do this.
public PeeringPropertyValidator getValidator(final NPPeerProperty property,
                                             final PeerPropertyLookup lookup) {
    return concatenate(getTypeValidator(property), getSpecificValidator(property, lookup));
}
```

The following examples compile but do not pass the Baseline Checkstyle configuration
because parameter `attempt` is re-assigned.

``` java
// BAD. Don't do this.
private void writeOutFile(TypedInput typedInputStream, int attempt) {
    if (attempt > MAX_CREATEFILE_ATTEMPTS) {
        throw new IllegalStateException("Failed after " + attempt + " attempts.");
    }

    try {
        writeStream(typedInputStream);
    } catch (Exception e) {
        writeOutFile(typedInputStream, attempt++); // Ouch. Infinite recursion.
    }
}
```

**note**:
Error handling in the above example as not ideal because the IllegalStateException
contains no information about the root cause of the failure. The author
should probably have chosen a non-recursive implementation to begin with.

### Avoid redundant modifiers

Redundant modifiers are discouraged, for example:

- Methods in interfaces are by definition `public` and `abstract`.
- Methods of final classes are already final.

### Avoid shadowing

Identifiers should not shadow other identifiers except in constructors
or setters.

### Limit coupling on concrete classes

In order to limit coupling on concrete classes, abstract classes as well
as certain canonical implementations of collections (e.g., HashSet,
HashMap, LinkedHashMap, LinkedHashSet, TreeSet, TreeMap) should be
avoided for variable, field, or return types.

### Make interfaces and signatures concise and specific

"Config" or "options" data structures, for example of type
`Map<String, Object>`, are often used to inject large numbers of
configuration parameters, for instance from a configuration file or
through a REST call. Don't be lazy and propagate this structure too far
down in your program as it creates hidden and not statically analyzable
dependencies. Instead, make interfaces and function signatures as
succinct and specific as possible, passing only the information the
function needs.

Example:

``` java
// BAD. Don't do this.
/**
  * A very clumsy explanation of expecting "username", "password", "expiryDate" keys in the {@code options} map,
  * probably omitting vital information on the expected types.
  */
public void createAccount(Map<String, Object> options) {
    String user = (String) options.get("userName"); // or "username" as above?
    String password = (String) options.get("password");

    // Need to be ugly here.
    Object o = options.get("expiryDate");
    if (!expiryDate instanceOf DateTime) {
        throw new IllegalArgumentException("Expecting expiry date to be a DateTime instance.");
    }
    DateString expiryDate = (DateTime) o;
    doSomethingWith(user, password, expiryDate);
}

createAccount(ImmutableMap.of("user", "Peter", "expires", "11.11.2011")); // The compiler won't help you.
```

``` java
// Good.
/**
  * Creates a new account for the specified user.
  * @param username ...
  * @param password ...
  * @param expiryDate ...
  */
public void createAccount(String username, String password, DateTime expiryDate) {
    Preconditions.checkNotNull(username);
    Preconditions.checkNotNull(password);
    Preconditions.checkNotNull(expiryDate);

    doSomethingWith(user, password, expiryDate);
}
```

**tip**

If your class/program/function takes non-trivial user-input, e.g., a configuration file,
command line parameters, a Hadoop configuration object to a MapReduce job, etc, then
consider specifying the expected input as a POJO and parsing the input into a Java
object as early as possible in your program flow. This gives you the ability to
fail fast and with useful error messages if the input is unexpected, and it helps
ensure that variables are well-typed in the remainder of your code. Example:

``` java
public class MyConfiguration {
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public final Map<String, Integer> userToId; // Can be ImmutableMap if Jackson Guava extension is registered.
    public final Path trustStorePath;

    @JsonCreator
    public MyConfiguration(@JsonProperty("userToId") Map<String, Integer> userToId,
                           @JsonProperty("trustStorePath") String trustStorePath) {
        this.userToId = userToId;
        this.trustStorePath = Paths.get(trustStorePath);
        Preconditions.checkState(
            Files.exists(this.trustStorePath), "Failed to access trust store: %s", trustStorePath);
    }

    public static MyConfiguration loadFromMap(Map<String, Object> map) {
        return objectMapper.convertValue(map, MyConfiguration.class);
    }

    public static MyConfiguration loadFromYaml(Path yamlConfigFile) {
        return objectMapper.convertValue(yamlConfigFile.toFile(), MyConfiguration.class);
    }
}
```

## Libraries

**Know them**. It pays off to read through the APIs of a couple of
standard Java libraries, e.g., Guava and few Apache Commons. We will
show some examples below of instances where knowledge of library
functions makes code cleaner, shorter, and more bug-free.

**Use them**. Someone has probably written this before. If you find a
(Palantir) library does *almost* does what you need, consider submitting
a patch to that library that generalizes it to cover the required
functionality. Don't forget to add a test for your addition. This
approach has two benefits: Firstly, you're less likely to introduce bugs
in your own, brand-new implementation, secondly, other developers can
benefit from your work in the future.

**note**:

We prefer **Guava** over **Apache Commons** and **Apache Commons** over re-implementing
functionality from scratch. Apache Commons Lang 2.x is deprecated; it must not be
introduced into codebases and should be replaced by version 3 wherever possible.

**Examples**.

``` java
// BAD. Don't do this.
StringBuilder builder = new StringBuilder();
int i = 0;
for (Map.Entry<String, String> item : map.entrySet()) {
    if (i != 0) builder.append(" ");
    builder.append(item.getKey()).append("=").append(item.getValue());
    i++;
}
String joined = builder.build();

// Good.
Joiner.MapJoiner joiner = Joiner.on(" ").withKeyValueSeparator("=");
String joined = joiner.join(map);
```

``` java
// BAD. Don't do this.
private static int countTrailingBackslashes(String str) {
    if (str.length()== 0) return 0;
    int count = 0;
    int length = str.length();
    while (length - count - 1 >= 0 && str.charAt(length - count - 1) == '\\') {
        count++;
    }
    return count;
}

// Good.
private static int countTrailingBackslashes(String str) {
    return StringUtils.indexOfAnyBut(StringUtils.reverse(str), '\\');
}
```

``` java
// BAD. Don't do this.
long startTime = System.nanoTime();
// Do stuff
long endTime = System.nanoTime();
System.out.println("Time Taken: " + (endTime - startTime) / Math.pow(10.0, 9.0) + " seconds.");

// Good.
Stopwatch stopwatch = Stopwatch.createStarted();
// Do stuff
System.out.format("Time Taken: %d s%n", stopwatch.elapsed(TimeUnit.SECONDS));
```

``` java
// BAD. Don't do this.
public static long getMillis(String durationString) {
    Preconditions.checkArgument(Pattern.compile("^\\d+[hdmy]$").matcher(durationString).find());
    char period = durationString.charAt(durationString.length() - 1);
    long number = Long.valueOf(durationString.substring(0, durationString.length() - 1));
    switch (period) {
        case 'h':
            return number * DateTimeConstants.MILLIS_PER_HOUR;
        case 'd':
            return number * DateTimeConstants.MILLIS_PER_DAY;
        case 'm':
            return number * 30 * DateTimeConstants.MILLIS_PER_DAY;
        case 'y':
            return number * 365 * DateTimeConstants.MILLIS_PER_DAY;
     }

     throw new IllegalStateException("Failed to parse duration string: " + durationString);
}

// Good.
public static long getMillis(String durationString) {
    // Note: Uses ISO periods instead of custom format on above.
    return ISOPeriodFormat.standard().parsePeriod(durationString).toStandardSeconds().getSeconds() * 1000L;
}
```

## Design Patterns

### Use the Builder Pattern

If a class has lots of overloaded constructors and/or one large
constructor which contains values for optional fields, it is confusing
to construct that class. The solution is (1) create the massive
constructor, but also (2) create a Builder class, and use the builder
rather than calling the massive constructor directly with empty values.
A builder class should take all required fields as parameters, then have
a set of methods which set optional arguments and return "this", and
finally have a "build" method that calls the real class's constructor.

If a method has lots of overloaded versions or lots of optional
arguments, (1) pull it out into its own class as a function object, and
(2) use the same pattern as above. In this case, you can replace the
"build" method with a "run" method that creates the class and runs its
method.

Example:

``` java
public final class Car {
    // Note that the builder pattern allows us to make cars immutable yet easy to construct and modify.
    private final int doors;
    private final ImmutableList<String> extras;

    private Car(int doors, ImmutableList<String> extras) {
        Preconditions.checkArgument(doors % 2 == 0, "Cars must be symmetric.");
        this.doors = doors;
        this.extras = extras;
    }

    // Convenience factory method that make code shorter: Car.builder() vs new Car.Builder().
    public static Car.Builder builder(int doors) {
        return new Car.Builder(doors);
    }

    public static Car.Builder convertible() {
        return new Car.Builder(2);
    }

    public static class Builder {
        // Users may construct a car builder as they wish, it's OK for fields to be public. Integrity constraints
        // are checked in the Car constructor.
        public int doors;  // required
        public ImmutableList.Builder<String> extras = ImmutableList.builder(); // optional

        // Ensure that required parameters are always set by providing only the relevant constructor.
        public Builder(int doors) {
            this.doors = doors;
        }

        // Allow users to "reverse-engineer" a Car and rebuild a pimped version.
        public Builder(Car car) {
            this.doors = car.doors;
        }

        public Car.Builder doors(int doors) {
            this.doors = doors;
            return this;
        }

        public Car.Builder withExtra(String extra) {
            extras.add(extra);
            return this;
        }

        public Car build() {
            return new Car(doors, extras.build());
        }
    }

    @Test
    public void testCar() {
        Car sportsCar = Car.convertible()
            .withExtra("Stero")
            .withExtra("Big engine")
            .build();

        Car sportsLimo = new Builder(sportsCar).doors(6).build();
    }
}
```

See *Effective Java, 2nd Edition, Item 2*

### Use the Visitor Pattern

Suppose we have a set of types that all implement a common interface.
Furthermore, suppose that:

- These types do not encapsulate their own behavior and depend on
    external classes for part of their functionality.
- The external classes that interact with these types need to behave
    differently depending on the particular type (i.e., we can't just
    pass in the common interface and expect a single code path to give
    us all of the desired functionality).
- We will potentially add types to this set as we develop
    new features.

As an example (inspired by this
[dzone](https://dzone.com/articles/design-patterns-visitor)
article), assume As a concrete example, we may have various Feeds
message types such as DocumentFeedMessage, ObjectFeedMessage, or
ObjectWatchMessage, all of which are implementations of the Message
interface.

- The Feeds message themselves are simple, but they may need to be
    rendered in HTML and stored to a database; to avoid cluttered API's
    for these Feeds message types, we delegate the rendering / database
    code to an external class
- The rendering process may be different depending on the particular
    type of a Feeds message
- We plan to add add new kinds of Feeds messages later on

Naively, we might consider doing something like this:

``` java
// Bad. Don't do this.
public string getXhtmlDocument(Message feedsMessage) {
    if (feedsMessage instanceof DocumentFeedMessage) {
        // render DocumentFeedMessage
        ...
    } else if (feedsMessage instanceof ObjectFeedMessage) {
        // render ObjectFeedMessage
        ...
    }
    ...
}
```

This works, but it's a bit ugly. Worse, it's hard to maintain,
especially if we plan to add new kinds of Feeds messages later on.

The **visitor pattern** is a vastly superior way to address our
requirements. Intuitively, we consider an external class that interacts
with the common interface to be a "visitor" of that interface; the
interface "accepts" these visitors and the visit plays out differently
depending on the particular implementation being visited. In other
words, "visiting" different types that implement a common interface
causes different code paths to be executed. At first glance, using the
visitor pattern to provide control flow might not seem significantly
more clear than the `instanceof` approach shown above. So why are we so
enthusiastic about the visitor pattern?

The main advantage of the visitor pattern is that it allows the compiler
to ensure that whenever we add a new type that implements the common
interface, we also update all of the external code that will interact
with objects of this new type. For example, say we implement a new Feeds
message type but forget to add the associated rendering / database code.
Using the visitor pattern will allow us to discover our mistake at
compile-time rather than run-time; this gives us a HUGE benefit when it
comes to avoiding subtle bugs, keeping code organized, and streamlining
the development process.

The following steps illustrate how to apply the visitor pattern to the
above example:

**Step 1**. Create a visitor interface corresponding to the common
interface (in this case, the common interface is Message so we create a
MessageVisitor):

``` java
public interface MessageVisitor {}
```

**Step 2**. Add an accept(visitor) method to the common interface:

``` java
public interface Message {
    ...
    void accept(MessageVisitor visitor);
}
```

**Step 3**. In all the types that implement the common interface,
implement the accept() method by having the visitor visit this:

``` java
@Override
public void accept(MessageVisitor visitor) {
    visitor.visit(this);
}
```

**Step 4**. Make the code compile by adding all the necessary methods to
the visitor interface. Be as specific about types as possible:

``` java
public interface MessageVisitor {
    public void visit(DocumentFeedMessage message);
    public void visit(ObjectFeedMessage message);
    public void visit(ObjectWatchMessage objectWatchMessage);
    public void visit(ResultLimitingMessage resultLimitingMessage);
    public void visit(SecurityChangeMessage securityChangeMessage);
}
```

**Step 5**. Each external class that interacts with the common interface
is considered a "visitor" of that common interface and implements the
visitor interface. For example, a class that renders Feeds message types
should implement the MessageVisitor interface. Furthermore, everywhere
in the code where you need to take different actions depending on the
particular type that implements the common interface, call
`accept(this)`. Note that you will often have one public method that is
meant for other code to actually call; the other public methods will
simply implement the visitor interface.

``` java
// Method that other code should call
public String getXhtmlDocument(Message message) {
    // This is where the "visit" happens; how the visit plays out depends
    // on the particular Feeds message type that was passed in
    message.accept(this);
    return XhtmlListItem.getXhtmlDocument(css, body);
}
...

// Implementing the visitor interface
@Override
public void visit(DocumentFeedMessage message) {
    final String imgSrc = FeedConstants.WEB_DOC_SEARCH_FEED_URL;
    final PTObjectContainer ptoc = loader.getObject(message);
    css = FeedHtmlUtils.getDefaultMessageCss();
    body = getDocFeedHtml(message.getReason(), ptoc, imgSrc);
}

@Override
public void visit(ObjectFeedMessage message) {
    final String imgSrc = FeedConstants.WEB_FILTER_FEED_URL;
    final PTObjectContainer ptoc = loader.getObject(message);
    css = FeedHtmlUtils.getDefaultMessageCss();
    body = getObjectHtml(message, message.getReason(), ptoc, imgSrc);
}
...
```

Now, whenever we add a new implementation of the common interface, we
will introduce compiler errors that help us track down all of the
necessary external changes. For example, if we add a new Feeds message
type (that implements Message), we will have to implement
accept(visitor), which will force us to add a new method to
MessageVisitor, which will then cause all of the implementations of
MessageVisitor to not compile until we've finished adding the necessary
rendering / database code.

## Inheritance

### Always use @Override

([Google Java Style Guide](https://github.com/google/styleguide),
section 6.1)

A method is marked with the `@Override` annotation whenever it is legal.
This includes a class method overriding a superclass method, a class
method implementing an interface method, and an interface method
re-specifying a super-interface method.

Exception: `@Override` may be omitted when the parent method is
`@Deprecated`.

### Favor composition over inheritance

Inheritance breaks encapsulation between the parent and child class,
increasing the difficulty of code changes. Whenever you have the option
of using composition instead, use it.

**Example 1**: Consider writing NumberOfElementsAddedHashSet that tracks
the number of elements added to it (the count is not affected by
removes). Let's say we extend HashSet. The method "add" will increment
addedCount. The method `addAll(Collection<T> c)` will increment
addedCount by `c.size()` and then delegate to `super.addAll(c)`. This
may or may not cause double-counting depending on whether
`HashSet#addAll` calls `HashSet#add`, which could change release to
release.

**Example 2**: Assume a class that parses Strings (e.g. from a CSV
file). The following are two ways to add a functionality that allows
users of the class to specify which lines are to be skipped. The
compositional approach is superior in pretty much every way: it's more
flexible, encourages reuse, easier to implement, more stable, etc pp.

``` java
// Inheritence example.
// BAD. Don't do this.
/**
 * Override this method to reject a line before parsing
 */
public boolean rejectLine(String line) {
    return false; // accept every line by default
}

public ParsedLine next() {
    String s = this.nextRawString();
    if (rejectLine(line)) return next();
    //  ... do parsing
}
```

``` java
// Composition example.

private<String> filter = Predicates.alwaysTrue(); // accept every line by default.
public void setFilter(Predicate<String> filter) {
    this.filter = filter;
}

public ParsedLine next() {
    String s = this.nextRawString();
    if (filter.apply(line) == false) return next();
    //  ... do parsing
}
```

See *Effective Java, 2nd Edition, Item 16*

### Design for extension or prohibit it

Subclasses overriding non-trivial methods are brittle for at least two
reasons:

- The subclass author may not be aware of implicit, private contracts
    and invariants that hold in the superclass. Overriding partial super
    class functionality often violates these contracts.
- Changes in the superclass can break the subclass if the
    above-mentioned private invariants change.

A class that can be subclassed is said to be *designed for extension* if
each of its non-private, non-static methods

- is abstract, or
- is final, or
- has an empty implementation.

The downside of these constraints is that subclasses are limited in
their flexibility.

If a subclass is *not* designed for extension, consider marking it as `final`.

**tip**

Consider replacing the abstracted methods of a class designed for
extension by an interface and injecting an implementation into the class
under consideration. Compare Prefer interfaces over abstract classes.

### Prefer interfaces over abstract classes

Interfaces are more flexible than abstract classes, because (1) A class
can implement multiple interfaces. (2) Interfaces better support the
decorator pattern. (3) Interfaces support dynamic proxies.

Even if you do define an abstract class, you should still also define an
interface and reference that where possible.

See *Effective Java, 2nd Edition, Item 18*

### Prefer class hierarchies to tagged classes

A class should represent one type of thing. If a class substantially
changes its behavior based on the value of one of its fields, then that
class should be multiple classes. Violations of this rule sacrifice
compile-time checking, are difficult to maintain internally, and are
incredibly confusing for other developers.

If the resulting classes truly expose the same interface as each other
and need to be passed into the same methods, then the origin class
should become an interface and/or abstract class, and each extracted
class should be an implementation of that. If it is always clear which
version of the class should be used in a given place, then the resulting
classes should not share an interface or abstract class.

See *Effective Java, 2nd Edition, Item 20*

### Bounded wildcards (PECS)

Short version of generics: for read-only collections, use "extends". For
write-only collections (output parameters), use "super". The more common
version is "Producer Extends, Consumer Super" .

Whenever you see a method that takes a collection with "super", you know
that the method is filling up that collection. Use this technique to
identify output parameters in code.

``` java
// Consumer extends.
void eat(List<? extends Vegetable> vegetables) {
    Vegetable vegetable = vegetables.get(0); // Safe for List<Carrot>, List<Cabbage>, ...
    swallow(vegetable);
}

// Producer super.
void harvest(List<? super Vegetable> vegetables) {
    Vegetable vegetable = Farm.harvest();
    vegetables.put(vegetable); // Safe for List<Edible>, List<Produce>, ...
}
```

See *Effective Java, 2nd Edition, Item 28* and Oracle's [Guidelines for
Wildcard
Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html).

## Enums

### Use enums instead of int constants

For a fixed set of things, use an Enum. They are strongly typed and can
support additional fields and methods as time goes on.

See *Effective Java, 2nd Edition, Item 30*

### Use instance fields instead of ordinals

Never write code that relies on enum ordering, because it will break and
you won't know why. Instead, pass a unique value to the enum's
constructor, and mark that field with `@UniqueValue`.

See *Effective Java, 2nd Edition, Item 31*

### Never miss a case

Switch statements are often used to take an action depending on the
value of an enum variable:

``` java
// BAD. Don't do this.
enum Colors {
    GREEN,
    BLUE
}

void printWithColor(Color color) {
    switch (color) {
        case GREEN:
            printInGreen();
            break;
        case BLUE:
            printInBlue();
            break;
    }
}
```

This pattern is fragile since the introduction of new enum values has
ripple-effects throughout the entire code base that cannot be statically
checked:

``` java
enum Colors {
    GREEN,
    BLUE,
    RED
}

printWithColor(RED);  // Compiles and runs fine, but does not do anything.
```

A common recipe to circumnavigate Java's lack of statically enforced
exhaustive pattern matching is to guard the switch statement at run-time
by throwing an exception in the `default` case to ensure that all cases
are handled:

``` java
// Good.
enum Colors {
    GREEN,
    BLUE,
    RED
}

void printWithColor(Color color) {
    switch (color) {
        case GREEN:
            printInGreen();
            break;
        case BLUE:
            printInBlue();
            break;
        case RED:
            printInRed();
            break;
        default:
            throw new IllegalStateException("Encountered unhandled Color: " + color);
    }
}
```

## Exception Handling

### Checked and unchecked exceptions

Use checked exceptions sparingly and wisely as they tend to have
non-local ripple effects and pollute APIs. Checked exceptions
propagating through multiple levels of API calls are generally a red
flag. Checked exceptions should only be used to indicate error
conditions that a caller can not influence or avoid and can/should recover
from; otherwise, throw an unchecked exception.

Only declare exceptions that make sense for the abstraction. For
example, a class that has nothing to do with Databases should never
throw an `SqlException`.

See *Effective Java, 2nd Edition, Item 58*

### Don't ignore exceptions

Except as noted below, it is very rarely correct to do nothing in
response to a caught exception. If the caller cannot recover from the
error condition, a typical response is to re-throw the caught exception
as an unchecked exception. When re-throwing exceptions, avoid logging
the stack trace since it will usually get logged further up the call
stack.

When it truly is appropriate to take no action whatsoever in a catch
block, the rationale is justified and explained in a comment.

``` java
try {
    int i = Integer.parseInt(response);
    return handleNumericResponse(i);
} catch (NumberFormatException expected) {
    // It's not numeric; that's fine, just continue.
}
return handleTextResponse(response);
```

**warning**: Never catch and ignore `InterruptedException`.

See *Effective Java, 2nd Edition, Item 65*

### Throwable, Error, RuntimeException: Not declared

You may throw exceptions of type `Throwable, Error, RuntimeException`
(although a more specific exception is probably preferred), but you
should not declare methods to throw them. `Error` and `RuntimeException`
are usually not intended to by caught, so there is no point in
specifying that they can be thrown.

### Exceptions: always immutable

Exception classes are always immutable.

### Try/catch blocks: never nested

Nested `try/catch` blocks are discouraged as they obfuscate control
flow.

## Log Levels

Always use one of the standard log levels when logging. The log levels,
from most severe to least severe, are:

**Fatal**: The application is unable to continue running, or is
completely unusable. If automated log monitoring is in place, a Fatal
log message will typically result in an admin being paged immediately.
Only use this for extremely critical failures that would warrant
potentially waking an admin up in the middle of the night.

**Error**: A problem occurred, but the application can still continue.
Errors must be actionable, i.e., each error must be associated with a
clear action to be taken. The problem does not need to be addressed
immediately, but should be looked into as soon as possible. For example,
a plugin that provides an important piece of the workflow failing to
initialize would be logged at the error level. The action to be taken is
to fix the plugin so that it initializes. If automated log monitoring is
in place, an Error log message will typically result in an automatic
email or ticket.

**Warn**: Something unusual happened, but may or may not be an actual
problem, and no immediate action must be taken. For example, failing to
find some configuration that should be specified, but can fall back to
defaults, would be logged at the warn level. If automatic log monitoring
is in place, surrounding warn logs may be included as additional context
for the alerts fired by more severe log messages.

**Info**: Coarse-grained information to provide additional context about
the state of the system. For example, the state of a configuration that
was loaded, or the state of a feature flag that was evaluated, would be
logged at the info level. Typically, prod mode logs will be set to info
level.

**Debug**: Fine-grained logs that are useful for developers. For
example, the number of results in some set would be logged at the debug
level. These logs will generally be disabled in production.

**Trace**: Even finer-grained logs than debug. For example, every id in
some set would be logged at the trace level. These logs will generally
be disabled in production.

## Concurrency

### Before you start: Read the books

Learn modern Java concurrency primitives and utilities by reading *Java
Concurrency in Practice*.

### Use higher-level abstracttions

Use Executors, Tasks, and Java Concurrency Utilities; not Threads,
ThreadGroups, synchronize, wait, notify, etc. If you are on Java 8, study
and use its concurrency mechanisms such as streams.

**tip**: Don't use low-level concurrency mechanisms since getting those right is
**very** hard. If you absolutely must use them, learn *Java Concurrency
in Practice* by heart, prove the correctness of your implementation
by referencing the JVM specification, and then don't use them.

See *Effective Java, 2nd Edition, Items 68-69*

### Prefer simple synchronization schemes

Concurrency bugs are very difficult to reliably fix, as their
manifestations are very transient. By writing very simple
synchronization schemes, code inspection is much more effective to
detect and fix these issues. (Tools like Google's error-prone will also perform
better at detecting issues.)

Often times it is best to simply avoid explicit synchronization in the
first place. This can often be accomplished by taking advantage of
executor services and other primitives provided by the
`java.util.concurrent` libraries.

### Always ensure your synchronization scheme is correct

Think very carefully about the correctness of your synchronization
scheme. If you can't "prove" to yourself that your synchronization
scheme is correct, then it probably isn't. Note that "prove" is in
quotes here — we're not expecting a detailed mathematical proof, just
some sort of a tight argument.

Make sure all data that is accessed by multiple threads is protected by
a lock, marked final/immutable, or marked volatile. Ensure that data is
published safely across threads. If you don't know what "published"
means in this context, please read part 1 of *Java Concurrency In
Practice* before continuing.

Make sure your scheme is robust to unexpected exceptions. Don't forget
to think about deadlock and starvation. Did we mention getting
concurrency right is very hard?

### Avoid leaking 'this' from constructors

'Leaking this' refers to a constructor that allows a reference to itself
to escape scope before the constructor completes:

``` java
public Foo(@Nonnull Bar bar, @Nonnull Baz baz) {
    // ...
    bar.setFoo(this);
    // ...
    assert baz != null;
    this.baz = baz;
}
```

One particularly subtle way to leak this is to create an anonymous inner
class from within a constructor, because inner classes contain a hidden
reference to the enclosing instance:

``` java
public Foo(Bar bar) {
    // Don't create an anonymous inner class in a constructor! Doing so publishes a hidden
    // reference to the enclosing object.
    new Runnable() {
        @Override
        public void run() {
            Foo enclosing = Foo.this;
            // enclosing was not yet properly constructed
        }
    }
    // ...
}
```

When this is leaked, it is possible for other objects to see an
inconsistently created object.

``` java
public class Bar {
    public void setFoo(Foo foo) {
        // Calling this on a partially constructed Foo will throw an NPE
        // that "is not possible" for legitimate Foo objects.
        foo.getBaz().doStuff();
    }
}
```

Leaking this is especially dangerous when multiple threads are involved,
because it is considered an unsafe publish. This can result in
(theoretically) permanently incoherently constructed objects, such as a
Foo whose 'baz' is always null.

The easiest way to avoid leaking this is to do the work in a static factory
method (see [Avoid doing work in
constructors](#avoid-doing-work-in-constructors) below).

See *Java Concurrency in Practice, section 3.2 "Publication and Escape"*

## Testing

### Use appropriate assertion methods

AssertJ and JUnit provide a variety of different static methods for
asserting equality, null/not-null, etc, between variables. While
different methods may assert the same fact -- e.g.,
`assertTrue(addOne(1) == 2)`, `assertEquals(addOne(1), 2)`, and
`assertThat(addOne(1)).isEqualTo(2)` -- they often differ in the type of error
message produced if the assertion fails. Choose the method that produces
the most useful error message, for example:

  -------------------------------- ------------------------
  **BAD. Don't do this.**          **Good.**
  assertEquals(false, method());   assertFalse(method());
  assertEquals(null, method());    assertNull(method());
  assertEquals(true, method());    assertTrue(method());
  assertTrue(a == b);              assertEquals(a, b);
  assertFalse(a != b);             assertEquals(a, b);
  -------------------------------- ------------------------

### Avoid assertNotNull

`assertNotNull(foo())` is often an indication of a lazily-written
so-called test in order to satisfy code coverage. Instead, validate the
expected output: `assertEquals(foo(), foo)`, `assertFalse(bar())`, etc.

## Dependency Injection

### Make constructor parameters specific, avoid God-objects

This mandates that your dependencies are known to users of your objects,
and makes it much easier to mock-test your code.

``` java
// BAD. Don't do this.
public Processor(Context context) {
    this.service = context.construct("other", OtherService.class);
    this.config = context.getConfig();
}
public process(String row) {
    if (!config.isDryRun()) {
      service.doProcess(row);
    }
}

// Good.
public Processor(OtherService service, boolean isDryRun) {
    this.service = service;
    this.isDryRun = isDryRun;
}
public process(String row) {
    if (!isDryRun) {
      service.doProcess(row);
    }
}
```

The "BAD" example has two main issues: First, it's hard to understand how to test the `Processor` since we'd have to
introspect deep into the code in order to understand what functionality of the `Context` we need to mock. Secondly, it
introduces broad coupling between `Processor`, `Context`, and all other classes using a `Context`. Refactoring becomes
painful and it hard to understand how the `Context` is used throughout the application.


### Create service proxies from config in one place only

Assume two service resources, `FooResource` and `BarResource` that each have a dependency on a service proxy
`OtherService`. Prefer constructing the `OtherService` instance at the top-level (often called "Server") and passing it
into the two resources, rather than passing the configuration into the resources and creating the services there.

``` java
// BAD. Don't do this.
public Server(Config config) {
    register(new FooResource(config));
    register(new BarResource(config));
}
public FooResource(Config config) {
    this.otherService = ClientFactory.fromConfig(config.get("OtherService"));
}
public BarResource(Config config) {
    this.otherService = ClientFactory.fromConfig(config.get("OtherService"));
}

// Good.
public Server(Config config) {
    OtherService service = ClientFactory.fromConfig(config.get("OtherService"));
    register(new FooResource(service));
    register(new BarResource(service));
}
public FooResource(OtherService service) {
    this.otherService = service;
}
public BarResource(OtherService service) {
    this.otherService = service;
}
```

This approach (1) makes it easy to test `FooResource` and `BarResource` with mocked `OtherService` implementations, and
(2) reduces the coupling between the `Server`, the `Config`, and the `Resource` classes.


### Prefer acyclic dependency graphs

```java
// BAD. Don't do this.
public Server(Config config) {
    this.port = config.port();
    register(new FooResource(this));
}
public FooResource(Server server) {
    this.name = "FooResource on port " + server.port();
    if (server.isAdminModeEnabled()) {
        server.register(new FooAdminResource());
    }
}

// Good.
public Server(Config config) {
    register(new FooResource("FooResource on port " + config.port));
    if (config.isAdminModeEnabled()) {
        register(new FooAdminResource());
    }
}
public FooResource(String name) {
    this.name = name;
}
```

Acyclic dependency graphs are hard to understand, hard to setup for tests and mocks, and introduce subtle code ordering
constraints. For example, switching the order of the constructor statements introduces a bug where the resource name is
always "FooResource on port 0" since `Server#port` hasn't been initialized when the resource calls `Server#port()`:

```java
// BAD. Don't do this.
public Server(Config config) {
    register(new FooResource(this));  // reads this.port which is initialized to 0.
    this.port = config.port();  // dead code.
}
```

### Avoid doing work in constructors

If you want to do real work in a constructor, consider putting the work
in a static factory method instead. Ensuring that your constructors are
cheap and fast also makes it easier to review for correctness.

Exception: Constructors may allocate new value objects, e.g. create an
`ArrayList`.

### Ensure objects are fully initialized after construction

Objects should not have an `initialize()`-style method which must be
invoked before the object is fully created. This leads to code that is
hard to reliably re-use, as there is no compile-time checking that the
object is properly initialized.

If your code would otherwise require an `initialize()` call
post-construction, consider using the Factory pattern (with appropriate
visibility scoping to ensure that the object cannot be constructed
without the Factory).

### Static fields should be immutable and final

Never use static fields that are not both immutable and final.

Referencing static state makes code too tightly coupled to external
factors. Because of this, even with unit tests, it is hard to state
facts about correctness, because the static state might not have the
same semantics in production as in tests.

### Avoid non-trivial static methods

Avoid static methods unless they are trivial, fast, and free from
dependencies. Violating any of these three constructs makes it hard to
unit test functionality.

### Refer to objects by their interfaces

Many of the benefits of interfaces (decorators, dynamic proxies) only
work when the code that uses the objects refers to them only by
interface and not by implementation. In general, for objects that have
behavior, you should only refer to an actual class name when calling a
constructor, and immediately assign the result of that constructor to a
field declared as an interface. The exception to this is data objects
such as Strings, primitives, and POJOs (plain old java objects).

See *Effective Java, 2nd Edition, Item 52*

### Prefer a single constructor

It follows from [Avoid doing work in
constructors](#avoid-doing-work-in-constructors) that a class should only have
one constructor. Multiple constructors imply that work is being done inside at
least one of them. Additionally, the purpose of the different constructors is
hidden because constructors don't have names. Static factories are an easy way
to get around this problem. Additionally, when there's a static factory for a
class, it's helpful to hide the constructor so that users know to call the
factory. Referencing the example in Constructor parameters should be interfaces,
data objects, or primitive types\_:

``` java
public static FeedsRowProcessor create(PalantirContext context) {
    return new FeedsRowProcessor(context.getSystemProperties(), context.getObjectStore(), context.getUiBuilder());
}

private FeedsRowProcessor(SystemPropertiesInterface sysprops, ObjectStore objectStore, UIBuilder builder) {
    /* assignment */
}
```

It may be necessary to expose the constructor for testing. Typically,
the constructor is made package-private and annotated with
`@VisibleForTesting`.

### Dependency injection frameworks

A range of orchestration frameworks for dependency injection and
configuration are available -- from lightweight (e.g., Google's
[Guice](https://github.com/google/guice)) to pretty hefty (e.g.,
[Spring](http://projects.spring.io/spring-framework/)). The particular
choice of framework can have long-range implications on code design and
application architecture, so it's usually best to consult someone with
prior experience before making a choice.
