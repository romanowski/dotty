---
layout: doc-page
title: Rules for Operators
---

The rules for infix operators have changed in some parts:

First, an alphanumeric method can be used as an infix operator only if its definition carries an `@infix` annotation. Second, it is recommended (but not enforced) to
augment definitions of symbolic operators with `@targetName` annotations. Finally,
a syntax change allows infix operators to be written on the left in a multi-line expression.

## The @infix Annotation

An `@infix` annotation on a method definition allows using the method as an infix operation. Example:
```scala
import scala.annotation.{infix, targetName}

trait MultiSet[T] {

  @infix
  def union(other: MultiSet[T]): MultiSet[T]

  def difference(other: MultiSet[T]): MultiSet[T]

  @targetName("intersection")
  def *(other: MultiSet[T]): MultiSet[T]
}

val s1, s2: MultiSet[Int]

s1 union s2         // OK
s1 `union` s2       // also OK but unusual
s1.union(s2)        // also OK

s1.difference(s2)   // OK
s1 `difference` s2  // OK
s1 difference s2    // gives a deprecation warning

s1 * s2             // OK
s1 `*` s2           // also OK, but unusual
s1.*(s2)            // also OK, but unusual
```
Infix operations involving alphanumeric operators are deprecated, unless
one of the following conditions holds:

 - the operator definition carries an `@infix` annotation, or
 - the operator was compiled with Scala 2, or
 - the operator is followed by an opening brace.

An alphanumeric operator is an operator consisting entirely of letters, digits, the `$` and `_` characters, or
any unicode character `c` for which `java.lang.Character.isIdentifierPart(c)` returns `true`.

Infix operations involving symbolic operators are always allowed, so `@infix` is redundant for methods with symbolic names.

The `@infix` annotation can also be given to a type:
```
@infix type or[X, Y]
val x: String or Int = ...
```

### Motivation

The purpose of the `@infix` annotation is to achieve consistency across a code base in how a method or type is applied. The idea is that the author of a method decides whether that method should be applied as an infix operator or in a regular application. Use sites then implement that decision consistently.

### Details

 1. `@infix` is defined in package `scala.annotation`.

 2. If a method overrides another, their infix annotations must agree. Either both are annotated with `@infix`, or none of them are.

 3. `@infix` annotations can be given to method definitions. The first non-receiver parameter list of an `@infix` method must define exactly one parameter. Examples:

    ```scala
    @infix def op(x: S): R                  // ok
    @infix def op[T](x: T)(y: S): R         // ok
    @infix def op[T](x: T, y: S): R         // error: two parameters

    @infix def (x: A) op (y: B): R          // ok
    @infix def (x: A) op (y1: B, y2: B): R  // error: two parameters
    ```

 4. `@infix` annotations can also be given to type, trait or class definitions that have exactly two type parameters. An infix type like

    ```scala
    @infix type op[X, Y]
    ```

    can be applied using infix syntax, i.e. `A op B`.

 5. To smooth migration to Scala 3.0, alphanumeric operators will only be deprecated from Scala 3.1 onwards,
or if the `-source 3.1` option is given in Dotty/Scala 3.

## The @targetName Annotation

It is recommended that definitions of symbolic operators carry a [@targetName annotation](../other-new-features/targetName.html) that provides an encoding of the operator with an alphanumeric name. This has several benefits:

 - It helps interoperability between Scala and other languages. One can call
   a Scala-defined symbolic operator from another language using its target name,
   which avoids having to remember the low-level encoding of the symbolic name.
 - It helps legibility of stacktraces and other runtime diagnostics, where the
   user-defined alphanumeric name will be shown instead of the low-level encoding.
 - It serves as a documentation tool by providing an alternative regular name
   as an alias of a symbolic operator. This makes the definition also easier
   to find in a search.

## Syntax Change

Infix operators can now appear at the start of lines in a multi-line expression. Examples:
```scala
val str = "hello"
  ++ " world"
  ++ "!"

def condition =
     x > 0
  || xs.exists(_ > 0)
  || xs.isEmpty
```
Previously, these expressions would have been rejected, since the compiler's semicolon inference
would have treated the continuations `++ " world"` or `|| xs.isEmpty` as separate statements.

To make this syntax work, the rules are modified to not infer semicolons in front of leading infix operators.
A _leading infix operator_ is
 - a symbolic identifier such as `+`, or `approx_==`, or an identifier in backticks,
 - that starts a new line,
 - that precedes a token on the same line that can start an expression,
 - and that is immediately followed by at least one space character `' '`.

Example:

```scala
    freezing
  | boiling
```
This is recognized as a single infix operation. Compare with:
```scala
    freezing
  !boiling
```
This is seen as two statements, `freezing` and `!boiling`. The difference is that only the operator in the first example
is followed by a space.

Another example:
```scala
  println("hello")
  ???
  ??? match { case 0 => 1 }
```
This code is recognized as three different statements. `???` is syntactically a symbolic identifier, but
neither of its occurrences is followed by a space and a token that can start an expression.
