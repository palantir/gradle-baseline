# Java Style Guide

**NOTE**
This guide is heavily adopted from [Google's Java Style Guide](https://github.com/google/styleguide).
Some taste and preference tweaks have been made.

Code is more often read than written. Style guidelines exist to make
reading code more pleasant and efficient by standardizing formatting
across development teams. The style guide covers the following topics:

- [Java Style Guide](#java-style-guide)
  - [A Note on Provenance](#a-note-on-provenance)
  - [Introduction](#introduction)
    - [Terminology Notes](#terminology-notes)
    - [Guide Notes](#guide-notes)
  - [Source File Basics](#source-file-basics)
    - [File name](#file-name)
    - [File encoding: UTF-8](#file-encoding-utf-8)
    - [File length: 2000 lines](#file-length-2000-lines)
    - [Special characters](#special-characters)
      - [Line ending: LF](#line-ending-lf)
      - [Whitespace characters](#whitespace-characters)
      - [Special escape sequences](#special-escape-sequences)
      - [Non-ASCII characters](#non-ascii-characters)
  - [Source File Structure](#source-file-structure)
    - [Package statement](#package-statement)
    - [Import statements](#import-statements)
      - [No wildcard imports](#no-wildcard-imports)
      - [No line-wrapping](#no-line-wrapping)
      - [Ordering and spacing](#ordering-and-spacing)
      - [No unused imports](#no-unused-imports)
      - [Limit static imports](#limit-static-imports)
    - [Class declaration](#class-declaration)
      - [Exactly one top-level class declaration](#exactly-one-top-level-class-declaration)
      - [Class member ordering](#class-member-ordering)
        - [Overloads: never split](#overloads-never-split)
  - [Formatting](#formatting)
    - [Braces](#braces)
      - [Braces are used where optional](#braces-are-used-where-optional)
      - [Nonempty blocks: K & R style](#nonempty-blocks-k-r-style)
      - [Empty blocks: may be concise](#empty-blocks-may-be-concise)
    - [Blocks](#blocks)
      - [Block indentation: +4 spaces](#block-indentation-4-spaces)
      - [Empty blocks: documented](#empty-blocks-documented)
    - [One statement per line](#one-statement-per-line)
    - [Column limit: 120](#column-limit-120)
    - [Line-wrapping](#line-wrapping)
      - [Where to break](#where-to-break)
      - [Indent continuation lines at least +8 spaces](#indent-continuation-lines-at-least-8-spaces)
    - [Whitespace](#whitespace)
      - [Vertical whitespace](#vertical-whitespace)
      - [Horizontal whitespace](#horizontal-whitespace)
      - [Horizontal alignment: never required](#horizontal-alignment-never-required)
    - [Grouping parentheses: recommended](#grouping-parentheses-recommended)
    - [Specific constructs](#specific-constructs)
      - [Enum classes](#enum-classes)
      - [Variable declarations](#variable-declarations)
        - [One variable per declaration](#one-variable-per-declaration)
        - [Declared when needed, initialized as soon as possible](#declared-when-needed-initialized-as-soon-as-possible)
      - [Arrays](#arrays)
        - [Array initializers: can be "block-like"](#array-initializers-can-be-block-like)
        - [No C-style array declarations](#no-c-style-array-declarations)
      - [Switch statements](#switch-statements)
        - [Indentation](#indentation)
        - [Fall-through: commented](#fall-through-commented)
        - [The default case is present](#the-default-case-is-present)
      - [Annotations](#annotations)
      - [Comments](#comments)
        - [Block comment style](#block-comment-style)
        - [Line comments](#line-comments)
        - [Todo comments](#todo-comments)
      - [Modifiers](#modifiers)
      - [Numeric literals](#numeric-literals)
  - [Naming](#naming)
    - [Rules common to all identifiers](#rules-common-to-all-identifiers)
    - [Rules by identifier type](#rules-by-identifier-type)
      - [Package names](#package-names)
      - [Class names](#class-names)
      - [Method names](#method-names)
      - [Constant names](#constant-names)
      - [Non-constant field names](#non-constant-field-names)
      - [Parameter names](#parameter-names)
      - [Local variable names](#local-variable-names)
      - [Type variable names](#type-variable-names)
    - [Camel case: defined](#camel-case-defined)
  - [Javadoc](#javadoc)
    - [Javadoc Formatting](#javadoc-formatting)
      - [General form](#general-form)
      - [Paragraphs](#paragraphs)
      - [At-clauses](#at-clauses)
    - [The summary fragment](#the-summary-fragment)
    - [Where Javadoc is used](#where-javadoc-is-used)
      - [Exception: self-explanatory methods](#exception-self-explanatory-methods)
      - [Exception: overrides](#exception-overrides)
      - [Exception: volatile methods](#exception-volatile-methods)

**Guiding principles and religious debates**. We recognize that most
formatting and style rules described by this document will be subject to
widely varying opinions and frenetic debate. We submit that this debate
is of mostly religious nature and the significant outcome is to agree on
*a* style. The widest consensus is likely achieved by having based the
Palantir style guide heavily on the [Google Java Style
Guide](https://github.com/google/styleguide), which has emerged as the
de facto code style for many teams inside and outside Palantir.

One principle underlying the style recommendations is to favor concise
and condensed source code to maximize the amount of program logic that
is visible per unit of screen estate. For example,

``` java
// BAD. Don't do this.
int myMethod(int parameter)
{
    if (notValid(parameter))
    {
        return -1;
    }

    if (something())
    {
        return parameter;
    }
    else
    {
        return -parameter;
    }
}
```

occupies almost twice the screen real estate of the more concise,
equivalent version below:

``` java
int myMethod(int parameter) {
    if (notValid(parameter)) return -1;

    if (something()) {
        return parameter;
    } else {
        return -parameter;
    }
}
```

A second principle is to avoid formatting variants that can easily give
rise to bugs. For example, multi-line if/else statements requiring
brackets and fall-through switch statements are discouraged unless the
behavior is explicitly documented.

**Consistency**. Although Palantir-wide consistency is great, it is not
as important as consistency within a product, module, or file:

- Value internal consistency higher than alignment with the style
  guide: First, aim for consistency within a class or file, then
  within a module, and eventually within a product or even Palantir.
- When refactoring a file or module, use this opportunity to make it
  consistent with the style guide.
- When writing software from scratch, follow the style guide.

There are rare cases when following the style guide makes code *less
readable*. In such cases, value readability over consistency with the
guide.

## A Note on Provenance

This style guide derives heavily from the [Google Java Style
Guide](https://github.com/google/styleguide), which itself borrows from
Oracle's Java style guide. The major changes we've made to Google's
guide:

- Substitute "Palantir" for "Google"
- License or copyright information
- Block indentation: +4 spaces
- Column limit: 120
- Import statements
- Trailing Horizontal whitespace is discouraged
- Add a few minor sections
- Remove section 6 ("Programming Practices"), which have been
  incorporated into our separate "Java Coding Guidelines" page

## Introduction

This document provides the definition of Palantir's coding standards for
source code in the Java programming language. A Java source file is
described as being in Palantir Style if and only if it adheres to the
rules herein.

Like in other programming style guides, the issues covered span not only
aesthetic issues of formatting, but also other types of conventions or
coding standards as well. However, this document focuses primarily on
the **hard-and-fast** rules that we follow universally and avoids giving
*advice* that isn't clearly enforceable (whether by human or tool).

### Terminology Notes

In this document, unless otherwise clarified:

1. The term *class* is used inclusively to mean an "ordinary" class,
enum class, interface or annotation type (`@interface`).

2. The term *comment* always refers to *implementation* comments. We do
not use the phrase "documentation comments", instead using the common
term "Javadoc." Other "terminology notes" will appear occasionally
throughout the document.

### Guide Notes

Example code in this document is non-normative. That is, while the
examples are in Palantir Style, they may not illustrate the only stylish
way to represent the code. Optional formatting choices made in examples
should not be enforced as rules.

## Source File Basics

### File name

The source file name consists of the case-sensitive name of the
top-level class it contains, plus the `.java` extension.

### File encoding: UTF-8

Source files are encoded in **UTF-8**.

### File length: 2000 lines

(not in [Google Java Style Guide](https://github.com/google/styleguide))

Source files contain at most 2000 lines.

### Special characters

#### Line ending: LF

(not in [Google Java Style Guide](https://github.com/google/styleguide))

The newline character is LF ('Line feed' aka '\\n' aka 0x0A). The last
line of every file ends with LF with no additional blank lines.

#### Whitespace characters

Aside from the line terminator sequence, **the ASCII horizontal space
character (0x20)** is the only whitespace character that appears
anywhere in a source file. This implies that:

**Note:** All other whitespace characters in string and character
literals are escaped. Tab characters are **not** used for indentation.

#### Special escape sequences

For any character that has a special escape sequence (`\b`, `\t`, `\n`,
`\f`, `\r`, `\"`, `\'` and `\\`), that sequence is used rather than
the corresponding octal (e.g. `\012`) or Unicode (e.g. `\u000a`) escape.

#### Non-ASCII characters

For the remaining non-ASCII characters, either the actual Unicode
character (e.g. `∞`) or the equivalent Unicode escape (e.g. `\u221e`) is
used, depending only on which makes the code easier to read and
understand.

**Tip:** In the Unicode escape case, and occasionally even when actual
Unicode characters are used, an explanatory comment can be very helpful.

Examples:

- `String unitAbbrev = "μs"`;
  - Best: perfectly clear even without a comment.
- `String unitAbbrev = "u03bcs"; // "μs"`
  - Allowed, but there's no reason to do this.
- `String unitAbbrev = "u03bcs"; // Greek letter mu, "s"`
  - Allowed, but awkward and prone to mistakes.
- `String unitAbbrev = "u03bcs";`
  - Poor: the reader has no idea what this is.
- `return 'ufeff' + content; // byte order mark`
  - Good: use escapes for non-printable characters and comment if necessary.

**Tip:** Never make your code less readable simply out of fear that some
programs might not handle non-ASCII characters properly. If that should
happen, those programs are broken and they must be fixed.

## Source File Structure

A source file consists of, in order:

1. License or copyright information, if present
2. Package statement
3. Import statements
4. Exactly one top-level class

Exactly one blank line separates each section that is present.

### Package statement

The package name of internal as well as open source projects starts with
`com.palantir.<project name>`. The package statement is not
line-wrapped, i.e., the column limit (Column limit: 120) does not
apply to package statements.

Package annotations occur in dedicated `package-info.java` files only.

### Import statements

#### No wildcard imports

Wildcard imports, static or otherwise, are not used.

#### No line-wrapping

Import statements are not line-wrapped. The column limit
(Column limit: 120) does not apply to import statements.

#### Ordering and spacing

(changed from [Google Java Style
Guide](https://github.com/google/styleguide)) Import statements are
divided into the following groups, in this order, with each group
separated by a single blank line:

1. All static imports in a single group
2. All non-static imports in a single group

Within a group there are no blank lines, and the imported names appear
in ASCII sort order.

#### No unused imports

Unused or redundant imports should be removed.

#### Limit static imports

(not in [Google Java Style Guide](https://github.com/google/styleguide))

Static imports of project-specific methods limit readability as they
obfuscate method provenance. Static imports are thus discouraged with
the exception of folklore methods under `org.junit.Assert`,
`org.hamcrest.Matchers`, `org.assertj.core.api.Assertions`,
`org.mockito.Mockito`, Guava `Preconditions`, and Apache Commons Lang3
`Validate`.

### Class declaration

#### Exactly one top-level class declaration

Each top-level class resides in a source file of its own.

#### Class member ordering

The ordering of the members of a class can have a great effect on
learnability, but there is no single correct recipe for how to do it.
Different classes may order their members differently.

What is important is that each class order its members in *some* logical
order, which its maintainer could explain if asked. For example, new
methods are not just habitually added to the end of the class, as that
would yield "chronological by date added" ordering, which is not a
logical ordering.

##### Overloads: never split

When a class has multiple constructors, or multiple methods with the
same name, these appear sequentially, with no intervening members.

## Formatting

Terminology Note: block-like construct refers to the body of a class,
method or constructor. Note that, by Section
Array initializers: can be "block-like", any array initializer may
optionally be treated as if it were a block-like construct.

### Braces

#### Braces are used where optional

Braces are used with `if`, `else`, `for`, `do` and `while` statements,
even when the body is empty or contains only a single statement.

#### Nonempty blocks: K & R style

Braces follow the Kernighan and Ritchie style ("Egyptian brackets") for
nonempty blocks and block-like constructs:

- No line break before the opening brace.
- Line break after the opening brace.
- Line break before the closing brace.
- Line break after the closing brace if that brace terminates a
  statement or the body of a method, constructor or named class. For
  example, there is no line break after the brace if it is followed by
  else or a comma. Example:

``` java
return new MyClass() {
    @Override public void method() {
        if (condition()) {
            try {
                something();
            } catch (ProblemException e) {
                recover();
            }
        }
    }
};
```

A few exceptions for enum classes are given in Section Enum classes.

#### Empty blocks: may be concise

An empty block or block-like construct may be closed immediately after
it is opened, with no characters or line break in between (`{}`), unless
it is part of a multi-block statement (one that directly contains
multiple blocks: `if/else-if/else` or `try/catch/finally`).

Example:

``` java
void doNothing() {}
```

### Blocks

#### Block indentation: +4 spaces

(changed from [Google Java Style
Guide](https://github.com/google/styleguide))

Each time a new block or block-like construct is opened, the indent
increases by four spaces. When the block ends, the indent returns to the
previous indent level. The indent level applies to both code and
comments throughout the block. (See the example in
Nonempty blocks: K & R Style.)

#### Empty blocks: documented

Empty blocks (of any type) are rare and allowed only if a justification
of their existence is clearly documented, usually by a comment. For
example:

``` java
try {
    int i = Integer.parseInt(response);
    return handleNumericResponse(i);
} catch (NumberFormatException expected) {
    // It's not numeric; that's fine, just continue.
}
```

Empty `catch` blocks in test code are discouraged and should be replaced
by a verification of the expected error message.

### One statement per line

Each statement is followed by a line-break. Empty statements are
discouraged.

### Column limit: 120

(changed from [Google Java Style
Guide](https://github.com/google/styleguide))

The column limit is 120 characters. Except as noted below, any line that
would exceed this limit must be line-wrapped, as explained in Section
Line-wrapping.

There are at least two strong reasons for limiting the line length:

- Very long lines are hard to grok for humans
- Given a typical monitor and resolution, side-by-side diffs (e.g. in
  Stash or Gerrit) require soft-wrapping or scrolling when lines are
  larger than about 120 characters.

Exceptions:

1. Lines where obeying the column limit is not possible (for example, a
   long URL in Javadoc, or a long JSNI method reference).
2. package and import statements (see Sections Package statement
   and Import statements)
3. Command lines in a comment that may be cut-and-pasted into a shell.

### Line-wrapping

Terminology Note: When code that might otherwise legally occupy a single
line is divided into multiple lines, typically to avoid overflowing the
column limit, this activity is called line-wrapping.

There is no comprehensive, deterministic formula showing exactly how to
line-wrap in every situation. Very often there are several valid ways to
line-wrap the same piece of code.

**Tip**: Extracting a method or local variable may solve the problem without
the need to line-wrap.

#### Where to break

The prime directive of line-wrapping is: prefer to break at a higher
syntactic level. Also:

- When a line is broken at a non-assignment operator the break comes
  before the symbol. (Note that this is not the same practice used in
  Palantir Style for other languages, such as C++ and JavaScript.)
- This also applies to the following "operator-like" symbols: the dot
  separator (`.`), the ampersand in type bounds
  (`<T extends Foo & Bar>`), and the pipe in catch blocks
  (`catch (FooException | BarException e)`).
- When a line is broken at an assignment operator the break typically
  comes after the symbol, but either way is acceptable.
- This also applies to the "assignment-operator-like" colon in an
  enhanced for ("foreach") statement.
- A method or constructor name stays attached to the open parenthesis
  (`(`) that follows it.
- A comma (`,`) stays attached to the token that precedes it.

#### Indent continuation lines at least +8 spaces

(changed from [Google Java Style
Guide](https://github.com/google/styleguide))

When line-wrapping, each line after the first (each continuation line)
is indented at least +8 from the original line.

When there are multiple continuation lines, indentation may be varied
beyond +8 as desired. In general, two continuation lines use the same
indentation level if and only if they begin with syntactically parallel
elements.

Section Horizontal alignment: never required addresses the discouraged
practice of using a variable number of spaces to align certain tokens
with previous lines.

### Whitespace

#### Vertical whitespace

(changed from [Google Java Style
Guide](https://github.com/google/styleguide))

A single blank line appears:

- Between consecutive members (or initializers) of a class: fields,
  constructors, methods, nested classes, static initializers,
  instance initializers.
- Exception: A blank line between two consecutive fields (having no
  other code between them) is optional. Such blank lines are used as
  needed to create logical groupings of fields.
- Within method bodies, as needed to create logical groupings
  of statements.
- Optionally before the first member or after the last member of the
  class (neither encouraged nor discouraged).
- As required by other sections of this document (such as
  Section Import statements).

Two consecutive blank lines are permitted, but never required (or
encouraged). More than two consecutive blank lines are discouraged.

#### Horizontal whitespace

Beyond where required by the language or other style rules, and apart
from literals, comments and Javadoc, a single ASCII space also appears
in the following places only.

1. Separating any reserved word, such as if, for or catch, from an open
   parenthesis (`(`) that follows it on that line
2. Separating any reserved word, such as else or catch, from a closing
   curly brace (`}`) that precedes it on that line
3. Before any open curly brace (`{`), with two exceptions:
   - `@SomeAnnotation({a, b})` (no space is used)
   - `String[][] x = {{"foo"}};` (no space is required between `{{`, by
     item 8 below)
4. On both sides of any binary or ternary operator. This also applies
   to the following "operator-like" symbols:
- the ampersand in a conjunctive type bound:
    `<T extends Foo & Bar>`
- the pipe for a catch block that handles multiple exceptions:
    `catch (FooException | BarException e)`
- the colon (`:`) in an enhanced for ("foreach") statement
5. After `,:;` or the closing parenthesis (`)`) of a cast
6. On both sides of the double slash (`//`) that begins an
   end-of-line comment. Here, multiple spaces are allowed, but
   not required.
7. Between the type and variable of a declaration: `List<String> list`
8. Optional just inside both braces of an array initializer
- `new int[] {5, 6}` and `new int[] { 5, 6 }` are both valid

**Note:** Whitespace at the end of a line is discouraged.

#### Horizontal alignment: never required

Terminology Note: Horizontal alignment is the practice of adding a
variable number of additional spaces in your code with the goal of
making certain tokens appear directly below certain other tokens on
previous lines.

This practice is permitted, but is never required by Palantir Style. It
is not even required to maintain horizontal alignment in places where it
was already used.

Here is an example without alignment, then using alignment:

``` java
private int x; // this is fine
private Color color; // this too

private int   x;      // permitted, but future edits
private Color color;  // may leave it unaligned
```

**Tip:** Alignment can aid readability, but it creates problems for
future maintenance. Consider a future change that needs to touch
just one line. This change may leave the formerly-pleasing formatting
mangled, and that is allowed. More often it prompts the coder
(perhaps you) to adjust whitespace on nearby lines as
well, possibly triggering a cascading series of reformattings. That
one-line change now has a "blast radius." This can at worst result in
pointless busywork, but at best it still corrupts version history
information, slows down reviewers and exacerbates merge conflicts.

### Grouping parentheses: recommended

Optional grouping parentheses are omitted only when author and reviewer
agree that there is no reasonable chance the code will be misinterpreted
without them, nor would they have made the code easier to read. It is
not reasonable to assume that every reader has the entire Java operator
precedence table memorized.

### Specific constructs

#### Enum classes

After each comma that follows an enum constant, a line-break is
optional.

An enum class with no methods and no documentation on its constants may
optionally be formatted as if it were an array initializer (see Section
Array initializers: can be "block-like").

``` java
private enum Suit { CLUBS, HEARTS, SPADES, DIAMONDS }
```

Since enum classes are classes, all other rules for formatting classes
apply.

#### Variable declarations

##### One variable per declaration

Every variable declaration (field or local) declares only one variable:
declarations such as int a, b; are not used.

##### Declared when needed, initialized as soon as possible

Local variables are not habitually declared at the start of their
containing block or block-like construct. Instead, local variables are
declared close to the point they are first used (within reason), to
minimize their scope. Local variable declarations typically have
initializers, or are initialized immediately after declaration.

#### Arrays

##### Array initializers: can be "block-like"

Any array initializer may optionally be formatted as if it were a
"block-like construct." For example, the following are all valid (not an
exhaustive list):

``` java
new int[] {           new int[] {
    0, 1, 2, 3            0,
}                         1,
                          2,
new int[] {               3,
    0, 1,             }
    2, 3
}                     new int[]
                              {0, 1, 2, 3}
```

##### No C-style array declarations

The square brackets form a part of the type, not the variable:
`String[] args`, not `String args[]`.

#### Switch statements

Terminology Note: Inside the braces of a switch block are one or more
statement groups. Each statement group consists of one or more switch
labels (either `case FOO:` or `default:`), followed by one or more
statements.

##### Indentation

(changed from Google guide: +2 to +4)

As with any other block, the contents of a switch block are indented +4.

After a switch label, a newline appears, and the indentation level is
increased +4, exactly as if a block were being opened. The following
switch label returns to the previous indentation level, as if a block
had been closed.

##### Fall-through: commented

Within a switch block, each statement group either terminates abruptly
(with a `break`, `continue`, `return` or thrown exception), or is marked
with a comment to indicate that execution will or might continue into
the next statement group. Any comment that communicates the idea of
fall-through is sufficient (typically `// fall through`). This special
comment is not required in the last statement group of the switch block.
Example:

``` java
switch (input) {
    case 1:
    case 2:
        prepareOneOrTwo();
        // fall through
    case 3:
        handleOneTwoOrThree();
        break;
    default:
        handleLargeNumber(input);
}
```

##### The default case is present

Each switch statement includes a default statement group, even if it
contains no code. The default case is always the last case in a switch
statement.

#### Annotations

(changed from [Google Java Style
Guide](https://github.com/google/styleguide))

Annotations applying to a class, method or constructor appear
immediately after the documentation block, and each annotation is listed
on a line of its own (that is, one annotation per line). These line
breaks do not constitute line-wrapping (Section Line-wrapping), so the
indentation level is not increased. Example:

``` java
@Override
@Nullable
public String getNameIfPresent() { ... }
```

Exception: A single parameterless annotation may instead appear together
with the first line of the signature, for example:

``` java
@Override public int hashCode() { ... }
```

Annotations applying to a field also appear immediately after the
documentation block, but in this case, multiple annotations (possibly
parameterized) may be listed on the same line; for example:

``` java
@Partial @Mock DataLoader loader;
```

There are no specific rules for formatting parameter and local variable
annotations.

Annotation parameters are unnamed (if possible) and compact:

``` java
// BAD. Don't do this.
@SuppressWarnings(value = {"unchecked", "unused"})  // Redundant name.
@SuppressWarnings({"unchecked"})  // Redundant array.

// Good.
@SuppressWarnings({"unchecked", "unused"})
@SuppressWarnings("unchecked")
```

#### Comments

##### Block comment style

Block comments are indented at the same level as the surrounding code.
They may be in `/* ... */` style or `// ...` style. For multi-line
`/* ... */` comments, subsequent lines must start with \* aligned with
the \* on the previous line.

``` java
/*
 * This is          // And so           /* Or you can
 * okay.            // is this.          * even do this. */
 */
```

Comments are not enclosed in boxes drawn with asterisks or other
characters.

**Tip:** When writing multi-line comments, use the `/* ... */` style if you
want automatic code formatters to re-wrap the lines when necessary
(paragraph-style). Most formatters don't re-wrap lines
in `// ...` style comment blocks.

##### Line comments

(not in [Google Java Style Guide](https://github.com/google/styleguide))

Single-line comments can appear above or next to the respective code
fragment:

``` java
// Check all path components for validity.
for (Path component : path) {
    validate(component); // throws if invalid.
}
```

##### Todo comments

(not in [Google Java Style Guide](https://github.com/google/styleguide))

"Todo" comments carry the name or the **author** of the comment, not the
LDAP name of the person who is supposed to resolve it, and are formatted
as follows:

``` java
    // TODO rfink: This is a todo comment.
    compute();
```

#### Modifiers

Class and member modifiers, when present, appear in the order
recommended by the Java Language Specification:

`public protected private abstract static final transient volatile synchronized native strictfp`

#### Numeric literals

`long`-valued integer literals use an uppercase `L` suffix, never
lowercase (to avoid confusion with the digit `1`). For example,
`3000000000L` rather than `3000000000l`.

## Naming

### Rules common to all identifiers

Identifiers use only ASCII letters and digits, and in two cases noted
below, underscores. Thus each valid identifier name is matched by the
regular expression `\w+`.

In Palantir Style special prefixes or suffixes, like those seen in the
examples `name_`, `mName`, `s_name` and `kName`, are not used.

### Rules by identifier type

#### Package names

Package names are all lowercase, with consecutive words simply
concatenated together (no underscores). For example,
`com.example.deepspace`, not `com.example.deepSpace` or
`com.example.deep_space`.

#### Class names

Class names are written in `UpperCamelCase`.

Class names are typically nouns or noun phrases. For example,
`Character` or `ImmutableList`. Interface names may also be nouns or
noun phrases (for example, `List`), but may sometimes be adjectives or
adjective phrases instead (for example, `Readable`).

There are no specific rules or even well-established conventions for
naming annotation types.

Test classes are named starting with the name of the class they are
testing, and ending with `Test`. For example, `HashTest` or
`HashIntegrationTest`.

#### Method names

Method names are written in `lowerCamelCase`.

Method names are typically verbs or verb phrases. For example,
`sendMessage` or `stop`.

Underscores may appear in JUnit test method names to separate logical
components of the name. One typical pattern is
`test<MethodUnderTest>_<state>`, for example `testPop_emptyStack`. There
is no *One Correct Way* to name test methods.

#### Constant names

Constant names use `CONSTANT_CASE`: all uppercase letters, with words
separated by underscores. But what is a constant, exactly?

Every constant is a static final field, but not all static final fields
are constants. Before choosing constant case, consider whether the field
really feels like a constant. For example, if any of that instance's
observable state can change, it is almost certainly not a constant.
Merely intending to never mutate the object is generally not enough.
Examples:

``` java
// Constants
static final int NUMBER = 5;
static final ImmutableList<String> NAMES = ImmutableList.of("Ed", "Ann");
static final Joiner COMMA_JOINER = Joiner.on(',');  // because Joiner is immutable
static final SomeMutableType[] EMPTY_ARRAY = {};
enum SomeEnum { ENUM_CONSTANT }

// Not constants
static String nonFinal = "non-final";
final String nonStatic = "non-static";
static final Set<String> mutableCollection = new HashSet<String>();
static final ImmutableSet<SomeMutableType> mutableElements = ImmutableSet.of(mutable);
static final Logger logger = Logger.getLogger(MyClass.getName());
static final String[] nonEmptyArray = {"these", "can", "change"};
```

These names are typically nouns or noun phrases.

#### Non-constant field names

Non-constant field names (static or otherwise) are written in
`lowerCamelCase`.

These names are typically nouns or noun phrases. For example,
`computedValues` or `index`.

#### Parameter names

Parameter names are written in `lowerCamelCase`. One-character parameter
names should be avoided.

#### Local variable names

Local variable names are written in `lowerCamelCase`, and can be
abbreviated more liberally than other types of names.

However, one-character names should be avoided, except for temporary and
looping variables.

Even when final and immutable, local variables are not considered to be
constants, and should not be styled as constants.

#### Type variable names

Each type variable is named in one of two styles:

- A single capital letter, optionally followed by a single numeral
  (such as `E, T, X, T2`)
- A name in the form used for classes (see Section Class names),
  followed by the capital letter `T` (examples: `RequestT`,
  `FooBarT`).

### Camel case: defined

Sometimes there is more than one reasonable way to convert an English
phrase into camel case, such as when acronyms or unusual constructs like
"IPv6" or "iOS" are present. To improve predictability, Palantir Style
specifies the following (nearly) deterministic scheme.

Beginning with the prose form of the name:

- Convert the phrase to plain ASCII and remove any apostrophes. For
  example, "Müller's algorithm" might become "Muellers algorithm".
- Divide this result into words, splitting on spaces and any remaining
  punctuation (typically hyphens).
- Recommended: if any word already has a conventional camel-case
  appearance in common usage, split this into its constituent parts
  (e.g., "AdWords" becomes "ad words"). Note that a word such as "iOS"
  is not really in camel case per se; it defies any convention, so
  this recommendation does not apply.
- Now lowercase everything (including acronyms), then uppercase only
  the first character of:
  - each word, to yield upper camel case, or
  - each word except the first, to yield lower camel case
- Finally, join all the words into a single identifier.

Note that the casing of the original words is almost entirely
disregarded. Examples:

| Prose form              | Correct           | Incorrect         |
| ----------------------- | ----------------- | ----------------- |
| "XML HTTP request"      | XmlHttpRequest    | XMLHTTPRequest    |
| "new customer ID"       | newCustomerId     | newCustomerID     |
| "inner stopwatch"       | innerStopwatch    | innerStopWatch    |
| "supports IPv6 on iOS?" | supportsIpv6OnIos | supportsIPv6OnIOS |
| "YouTube importer"      | YouTubeImporter   | Youtubeimporter   |

**Note:** Some words are ambiguously hyphenated in the English language:
for example "nonempty" and "non-empty" are both correct, so the method
names checkNonempty and checkNonEmpty are likewise both correct.

## Javadoc

### Javadoc Formatting

#### General form

The basic formatting of Javadoc blocks is as seen in this example:

``` java
/**
 * Multiple lines of Javadoc text are written here,
 * wrapped normally...
 */
public int method(String p1) { ... }
```

... or in this single-line example:

``` java
/** An especially short bit of Javadoc. */
```

The basic form is always acceptable. The single-line form may be
substituted when there are no at-clauses present, and the entirety of
the Javadoc block (including comment markers) can fit on a single line.

#### Paragraphs

One blank line—that is, a line containing only the aligned leading
asterisk (`*`)—appears before the group of "at-clauses" if present.
Paragraphs are separated by a `<p>` tags as follows:

``` java
/**
 * This is paragraph one.
 * <p>
 * This is paragraph two.
 */
```

#### At-clauses

Any of the standard "at-clauses" that are used appear in the order
`@param`, `@return`, `@throws`, `@deprecated`, and these four types
never appear with an empty description. When an at-clause doesn't fit on
a single line, continuation lines are not indented.

### The summary fragment

The Javadoc for each class and member begins with a brief summary
fragment. This fragment is very important: it is the only part of the
text that appears in certain contexts such as class and method indexes.

This is a fragment -— a noun phrase or verb phrase, not a complete
sentence. It begins with a 3rd-person-singular verb such as "Returns
...", "Computes ...", "Saves ...", etc. It does not begin with
`A {@code Foo} is a...`, or `This method returns...`, nor does it form a
complete imperative sentence like `Save the record.`. However, the
fragment is capitalized and punctuated as if it were a complete
sentence.

**Tip:** A common mistake is to write simple Javadoc in the
form `/** @return the customer ID */`. This is incorrect,
and should be changed to `/** Returns the customer ID. */`.

### Where Javadoc is used

At the minimum, Javadoc is strongly encouraged for every public class
and interface. Javadoc is encouraged for every public or protected
member of a class, with a few exceptions noted below.

Other classes and members may have Javadoc as needed. Whenever an
implementation comment would be used to define the overall purpose or
behavior of a class, method or field, that comment is usually written as
Javadoc instead. (It's more uniform, and more tool-friendly.)

#### Exception: self-explanatory methods

Javadoc is discouraged for "simple, obvious" methods like `getFoo()`, in
cases where there really and truly is nothing else worthwhile to say but
"Returns the foo".

**Note:** It is not appropriate to cite this exception to justify
omitting relevant information that a typical reader
might need to know. For example, for a method named
`getCanonicalName`, don't omit its documentation (with the
rationale that it would say only
`/** Returns the canonical name. */`) if a typical reader may have
no idea what the term "canonical name" means.

#### Exception: overrides

Javadoc is not always present on a method that overrides a supertype
method.

#### Exception: volatile methods

(not in [Google Java Style Guide](https://github.com/google/styleguide))

Detailed Javadoc is discouraged for methods whose exact behavior is
subject to frequent change; in such cases, the cost of synchronizing
method behavior and Javadoc is high and out-of-sync Javadoc is worse
than no Javadoc. Javadoc may be present to indicate that method behavior
is defined by code or unit tests.
