# Contributor guide

http4s is an open source project, and depends on its users to continue
to improve.  We are thrilled that you are interested in helping!

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-generate-toc again -->
**Table of Contents**

- [Submitting issues](#submitting-issues)
- [Submitting pull requests](#submitting-pull-requests)
    - [Claim an issue](#claim-an-issue)
    - [Build the project](#build-the-project)
    - [Testing](#testing)
    - [Contributing documentation](#contributing-documentation)
    - [Targeting a branch](#targeting-a-branch)
    - [Attributions](#attributions)
    - [Grant of license](#grant-of-license)
- [Building the Community](#building-the-community)
    - [Join the adopters list](#join-the-adopters-list)
    - [Spread the word](#spread-the-word)
    - [Code of Conduct](#code-of-conduct)
- [Acknowledgements](#acknowledgements)

<!-- markdown-toc end -->

## Submitting issues

If you notice a bug, have an idea for a feature, or have a question
about the code, please don't hesitate to [create a GitHub issue].
Many of the good ideas in http4s were introduced by one user and later
implemented by another.

## Submitting pull requests

### Claim an issue

If there is already a GitHub issue for the task you are working on,
please leave a comment to let people know that you are working on it.
If there isn't already an issue and it is a non-trivial task, it's a
good idea to create one (and note that you're working on it). This
prevents contributors from duplicating effort.

### Build the project

First you'll need to checkout a local copy of the code base:

```sh
git clone git@github.com:http4s/http4s.git
```

To build http4s you should have [sbt installed].  Run `sbt`, and then
use any of the following commands:

 * `compile`: compile the code
 * `console`: launch a REPL
 * `test`: run the tests
 * `makeSite`: generate the documentation, including scaladoc.
 * `scalastyle`: run the style-checker on the code.

### Testing

- We use Specs2 and Scalacheck for our tests.
- Tests go under the `src/test/scala` in the module that is being
  tested.
- Most http4s tests should extend `Http4sSpec`.  `Http4sSpec` creates
  a sensible stack of `Specs2` traits and imports syntax and instances
  for convenience.
- Try to think of properties that should always hold for your code,
  and let Scalacheck generate your tests for you:
```scala
  "parses own string rendering to equal value" in {
    forAll(tokens) { token => fromString(token).map(_.renderString) must be_\/-(token) }
  }
```
- Some code is hard to write properties for, or the properties echo
  the implementation.  We accept and encourage traditional
  example-based tests as well.
- Code coverage stats are published to [codecov.io].  We don't enforce
  any arbitrary metrics, but the code coverage reports are helpful in
  finding where new tests can deliver the most value.

### Contributing documentation

Often neglected but always deeply appreciated, documentation is a
great way to begin contributing to http4s.

The documentation at [http4s.org] is stored alongside the source, in
the [docs subproject], where you will find a README that describes how
the site is developed.  For quick enhancements, most pages on the site
have an edit link directly to the GitHub source for quick cleanups.

### Targeting a branch

http4s actively maintains three branches:

* [master]: the mainline of development; where docs/src/hugo is published from
* [release-0.16.x]: the last scalaz-stream based release. Merges to [master].
* [release-0.15.x]: the current production release. Merges to [release-0.16.x].

The guide below helps find the most appropriate branch for your change.

My change is...                               | Branch
----------------------------------------------|-------------------
Documentation of existing features            | [release-0.15.x]
Documentation of unreleased features          | [release-0.16.x]
Binary compatible with current release        | [release-0.15.x]
Binary incompatible with current release      | [release-0.16.x]
Specific to cats or fs2                       | [master]
Change to docs/src/hugo                       | [master]

Still unsure?  Don't worry!  Send us that PR, and we'll cherry-pick it
to the right place.

After v0.16.0, we will simplify to maintaining two branches: the last
production release and the current master.

### Attributions

If your contribution has been derived from or inspired by other work,
please state this in its scaladoc comment and provide proper
attribution. When possible, include the original authors' names and a
link to the original work.

### Grant of license

http4s is licensed under the [Apache License 2.0]. Opening a pull
request signifies your consent to license your contributions under the
Apache License 2.0.

## Building the Community

### Join the adopters list

It's easy to [add yourself as an adopter].  You get free advertising
on the [adopters list], and a robust list encourages gives new users
the confidence to try http4s and grows the community for all.

### Spread the word

We watch [Gitter] and [GitHub issues] closely, but it's a bubble.  If
you like http4s, a blog post, meetup presentation, or appreciative
tweet helps reach new people, some of whom go on to contribute and
build a better http4s for you.

### Code of Conduct

Discussion around http4s is currently happening on [Gitter] as
well as [Github issues].  We hope that our community will be
respectful, helpful, and kind.  People are expected to follow the
[Typelevel Code of Conduct] when discussing http4s on Github,
Gitter, or in other venues.  If you find yourself embroiled in a
situation that becomes heated, or that fails to live up to our
expectations, you should disengage and contact one of the [community
staff] in private. We hope to avoid letting minor aggressions and
misunderstandings escalate into larger problems.

If you are being harassed, please contact one of the [community staff]
immediately so that we can support you.

## Acknowledgements

This document is heavily based on the [Cats contributor's guide].

[Apache License 2.0]: https://github.com/http4s/http4s/blob/master/LICENSE
[Cats contributor's guide]: https://github.com/typelevel/cats/blob/master/CONTRIBUTING.md
[Github issues]: https://github.com/http4s/http4s/issues
[Gitter]: http://gitter.im/http4s/http4s
[Typelevel Code of Conduct]: http://typelevel.org/conduct.html
[add yourself as an adopter]: https://github.com/http4s/http4s/edit/master/docs/src/hugo/content/adopters.md
[adopters list]: http://http4s.org/adopters/
[cats]: https://github.com/http4s/http4s/tree/cats
[codecov.io]: https://codecov.io/gh/http4s/http4s
[community staff]: http://http4s.org/community/conduct.html#community-staff
[create a GitHub issue]: https://github.com/http4s/http4s/issues/new
[docs subproject]: https://github.com/http4s/http4s/tree/master/docs
[http4s.org]: http://http4s.org/
[issues page]: https://github.com/http4s/http4s/issues
[master]: https://github.com/http4s/http4s/tree/master
[release-0.15.x]: https://github.com/http4s/http4s/tree/release-0.15.x
[sbt installed]: http://www.scala-sbt.org/0.13/tutorial/Setup.html
