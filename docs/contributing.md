
# Contributing


This guide is for people who would like to be involved in building
http4s.

## Find something that belongs in http4s

Looking for a way that you can help out? Check out our [issue
tracker].  Choose a ticket that looks interesting to you.  Before you
start working on it, make sure that it's not already assigned to
someone and that nobody has left a comment saying that they are
working on it!  Of course, you can also comment on an issue someone is
already working on and offer to collaborate.

[issue tracker]: https://github.com/http4s/http4s/issues

Have an idea for something new? That's great! We recommend that you
make sure it belongs in http4s before you put effort into creating a
pull request. The preferred ways to do that are to either:

* [Create a GitHub issue] describing your idea.
* Get feedback in the [http4s Gitter room].

[Create a GitHub issue]: https://github.com/http4s/http4s/issues/new
[http4s Gitter room]: https://gitter.im/http4s/http4s

## Let us know you are working on it

If there is already a GitHub issue for the task you are working on,
leave a comment to let people know that you are working on it. If
there isn't already an issue and it is a non-trivial task, it's a good
idea to create one (and note that you're working on it). This prevents
contributors from duplicating effort.

## Build the Project

First you'll need to checkout a local copy of the code base:

```sh
git clone git@github.com:http4s/http4s.git
```

To build http4s, you should have [SBT] and [Hugo] installed.  Run `sbt ci`.
This runs:

* `test`: compiles all code and runs the unit tests
* `makeSite`: compiles the tutorial, generates the scaladoc, and
  builds the static site.
* `mimaReportBinaryIssues`: checks for binary compatibility changes,
  which are relevant past patch release .0.

[SBT]: http://www.scala-sbt.org/0.13/tutorial/Setup.html
[Hugo]: https://gohugo.io/getting-started/installing/

## Coding Standard

### Formatting

The Travis CI build verifies that code is formatted correctly
according to the [Scalafmt] config and will fail if a diff is found.

You can run `validate` to test the formatting before opening a PR.  If
your PR fails due to formatting, run `;test:scalafmt`.

[Scalafmt]: http://scalameta.org/scalafmt/

#### IntelliJ IDEA specific settings

To setup IntelliJ IDEA to conform with the formatting used in this project,
the following settings should be changed from the default.

Under `Settings > Editor > Code Style > Scala`:

* Set `Formatter` to `scalafmt`. The default path for the `.scalafmt.conf`
file should work, if not, point it to the `.scalafmt.conf` in the root of
the project.
* In the `Imports` tab, in the `Import layout` pane, delete all entries,
except for `all other imports`. This disables the grouping and sorting of
imports that IntelliJ does by default.

### Types

#### Effects

Prefer a parameterized effect type and cats-effect type classes over
specializing on a task.

```scala
// Good
def apply[F[_]](service: HttpApp[F])(implicit F: Monad[F]): HttpApp[F]

// Bad
def apply(service: HttpApp[IO]): HttpService[IO]
```

For examples and tutorials, use `cats.effect.IO` wherever a concrete effect is
needed.

#### Collections

Prefer standard library types such as `Option` and `List` to invariant
replacements from libraries such as Scalaz or Dogs.

When a list must not be empty, use `cats.data.NonEmptyList`.

#### `CIString`

Many parts of the HTTP spec require case-insensitive semantics. Use
`org.typelevel.ci.CIString` to represent these. This is important to
get correct matching semantics when using case class extractors.

### Case classes

#### `apply`

The `apply` method of a case class companion should be total. If this is
impossible for the product type, create a `sealed abstract class` and define
alternate constructors in the companion object. Make the implementation of the
sealed abstract class private.

Consider a macro for the `apply` method if it is partial, but literal arguments
can be validated at compile time.

#### Safe Constructors

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

#### Unsafe Constructors

All constructors that are partial on their input should be prefixed with `unsafe`.

```scala
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

## Attributions

If your contribution has been derived from or inspired by other work,
please state this in its scaladoc comment and provide proper
attribution.  When possible, include the original authors' names and a
link to the original work.

### Grant of License

http4s is licensed under the [Apache License 2.0]. Opening a pull
request signifies your consent to license your contributions under the
Apache License 2.0.

[Apache License 2.0]: https://www.apache.org/licenses/LICENSE-2.0.html

## Tests

* Tests for http4s-core go into the `tests` module.
* Tests should extend `Http4sSuite`.  `Http4sSuite` extends [MUnit]
  with all syntax, standard instances, and helpers for convenience.
* We use MUnit's integration with [ScalaCheck] for property testing.
  We prefer property tests where feasible, complemented by example
  tests where they increase clarity or confidence.
* We encourage the addition of arbitrary instances to
  `org.http4s.testing.Http4sArbitraries` to support richer property
  testing.
* For assertions in tests using `assertEquals(a, b)` is preferable to `assert(a == b)`. 
  It brings nice diffs on assertions failures. For more details, see the [MUnit docs].
* For time sensitive tests, where the passing of time is a factor in the logic that the
  test is checking on, and that operates in the `IO` context,  consider using the mock 
  runtime provided by `cats.effect.testkit.TestControl`. For more details check out the
  docs on [TestControl from cats.effect.testkit]

[MUnit]: https://scalameta.org/munit/
[ScalaCheck]: https://www.scalacheck.org/
[MUnit docs]: https://scalameta.org/munit/docs/assertions.html#assertequals
[TestControl from cats.effect.testkit]: https://typelevel.org/cats-effect/docs/core/test-runtime

## Documentation

The common area of http4s.org (i.e., directories not beginning with
`/v#.#`) is generated from the `docs/` directory and is published only
from the `main` branch. This module is intended to contain general
info about the project that applies to all versions.

Each branch, `main` and `series/X.Y`, publishes documentation per
minor version into the `/vX.Y` directory of http4s.org.  
The [mdoc] content lives in `docs/docs`.  
[mdoc] is used to typecheck our documentation as part of the build.

### Running the Site Locally

For generating a static site locally, run from within sbt:

```sh
site/tlSite
```

For starting a preview server with live updates, run from within sbt:

```sh
site/tlSitePreview
```

Now you can open a browser at http://localhost:4242/ to see the local version
of the site. At `http://localhost:4242/v{currentVersion}/` would be the local version
of the versioned part of the site (e.g. `http://localhost:4242/v0.23/`).

When you update any input sources, mdoc will detect this and compile the Scala code and write the modified Markdown
sources to its output directory which in turn Laika is watching. Note that when running `tlSitePreview` Laika does
not write any output to disk, it serves the site entirely from memory. And btw: it uses http4s for that.


## Submit a Pull Request

Before you open a pull request, you should make sure that `sbt ci` runs
successfully. Github Actions will run this as well, but it may save you some
rebasing. Squashing and rebasing can lead to a tidier git history, but
they can also be a hassle if somebody else has done work based on your
branch.

----

This guide borrows heavily from the [Cats' contributors guide][cats].

[cats]: https://github.com/typelevel/cats/blob/master/CONTRIBUTING.md
[mdoc]: https://scalameta.org/mdoc/
