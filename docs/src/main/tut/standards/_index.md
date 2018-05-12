---
type: common
menu: main
weight: 150
title: Coding Standard
---

## Types

### Effects

Prefer a parameterized effect type and cats-effect type classes over
specializing on IO. (In versions before cats-effect is on the classpath,
specialize on `fs2.Task` or `scalaz.concurrent.Task`.)

```scala
// Good
def apply[F[_]](service: HttpApp[F])(implicit F: Monad[F]): HttpApp[F]

// Bad
def apply(service: HttpApp[IO]): HttpApp[IO]
```

For examples and tutorials, use `cats.effect.IO` wherever a concrete effect is
needed.

### Collections

Prefer standard library types such as `Option` and `List` to invariant
replacements from libraries such as Scalaz or Dogs.

When a list must not be empty, use `cats.data.NonEmptyList`.

### `CaseInsensitiveString``

Many parts of the HTTP spec require case-insensitive semantics. Use
`org.http4s.util.CaseInsensitiveString` to represent these. This is important to
get correct matching semantics when using case class extractors.

## Case classes

### `apply`

The `apply` method of a case class companion should be total. If this is
impossible for the product type, create a `sealed abstract class` and define
alternate constructors in the companion object. Make the implementation of the
sealed abstract class private.

Consider a macro for the `apply` method if it is partial, but literal arguments
can be validated at compile time.

### Safe constructors

Constructors that take an alternate type `A` should be named `fromA`. This
includes constructors that return a value as a `ParseResult`.

```scala
case class Foo(seconds: Long)

object Foo {
  def fromFiniteDuration(d: FiniteDuration): Foo =
    apply(d.toSeconds)
    
  def fromString(s: String): ParseResult[Foo] =
    try s.toLong
    catch { case e: NumberFormatException => 
      new ParseFailure("not a long") 
    }
}
```

Prefer `fromString` to `parse`.

### Unsafe constructors

All constructors that are partial on their input should be prefixed with `unsafe`.

```
// Good
def fromLong(l: Long): ParseResult[Foo] =
  if (l < 0) Left(ParseFailure("l must be non-negative"))
  else Right(new Foo(l))
def unsafeFromLong(l: Long): Foo = 
  fromLong(l).fold(throw _, identity)

// Bad
def fromLong(l: Long): ParseResult[Foo] =
  if (l < 0) throw new ParseFailure("crash boom bang")
  else Right(new Foo(l))
```

Constructors prefixed with `from` may return either a `ParseResult[A]` or, if
total, `A`.

## Tests

We use Specs2 for example-based testing and its integration with scalacheck for
property testing.  Property tests and arbitrary instances are encouraged.
