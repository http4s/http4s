---
layout: page
title: Contributors' Guide
position: 4
---

# Contributors' guide

http4s is an open source project, and depends on its users to continue
to improve.  We are thrilled that you are interested in helping!

This guide assumes that you have some experience doing Scala
development. If you get stuck on any of these steps, please feel free
to [ask for help](#getting-in-touch).

## How can I help?

http4s follows a standard [fork and pull] model for contributions via
GitHub pull requests.

Below is a list of the steps that might be involved in an ideal
contribution. If you don't have the time to go through every step,
contribute what you can, and someone else will probably be happy to
follow up with any polishing that may need to be done.

If you want to touch up some documentation or fix typos, feel free to
skip these steps and jump straight to submitting a pull request.

 1. [Find something that belongs in http4s](#find-something-that-belongs-in-http4s)
 2. [Let us know you are working on it](#let-us-know-you-are-working-on-it)
 3. [Build the project](#build-project)
 4. [Implement your contribution](#write-code)
 5. [Write tests](#write-tests)
 6. [Write documentation](#write-documentation)
 7. [Write examples](#write-examples)
 8. [Submit pull request](#submit-a-pull-request)

### Find something that belongs in http4s

Looking for a way that you can help out? Check out our [issues page].
Before you start working on it, make sure that it's not already
assigned to someone and that nobody has left a comment saying that
they are working on it!  (Of course, you can also comment on an issue
someone is already working on and offer to collaborate.)

Have an idea for something new? That's great! We recommend that you
make sure it belongs in http4s before you put effort into creating a
pull request. The preferred ways to do that are to either:

 * [create a GitHub issue] describing your idea.
 * get feedback in the [http4s Gitter room](https://gitter.im/http4s/http4s).

### Let us know you are working on it

If there is already a GitHub issue for the task you are working on,
leave a comment to let people know that you are working on it. If
there isn't already an issue and it is a non-trivial task, it's a good
idea to create one (and note that you're working on it). This prevents
contributors from duplicating effort.

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
 * `scalastyle`: run the style-checker on the code

### Write code

TODO

### Attributions

If your contribution has been derived from or inspired by other work,
please state this in its ScalaDoc comment and provide proper
attribution. When possible, include the original authors' names and a
link to the original work.

### Write tests

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

## Contributing documentation

Often neglected but always deeply appreciated, documentation is a
great way to begin contributing to http4s.

The documentation at [http4s.org] is stored alongside the source, in
the [docs subproject], where you will find a README that describes how
the site is developed.

## Submit a pull request

### Grant of license

http4s is licensed under the [Apache License 2.0]. Opening a pull
request signifies your consent to license your contributions under the
Apache License 2.0.

### Targeting a branch

If your code change is binary compatible with the latest release, or
documents features in a release version, please target the
release-MAJOR.MINOR.x branch.  This allows us to create patch releases
and keeps the production documentation at its best.  A maintainer will
take care of merging from the release branch to master.

If your change adds or breaks API or documents unreleased features, or
you're just not sure, please target the master branch.

### Issue references

If your pull request addresses an existing issue, please tag that
issue number in the body of your pull request or commit message. For
example, if your pull request addresses issue number 52, please
include "fixes #52".

### Git history

If you make changes after you have opened your pull request, please
add them as separate commits and avoid squashing or
rebasing. Squashing and rebasing can lead to a tidier git history, but
they can also be a hassle if somebody else has done work based on your
branch.

## How did we do?

Getting involved in an open source project can be tough. As a
newcomer, you may not be familiar with coding style conventions,
project layout, release cycles, etc. This document seeks to demystify
the contribution process for the http4s project.

It may take a while to familiarize yourself with this document, but if
we are doing our job right, you shouldn't have to spend months poring
over the project source code or lurking the [Gitter room] before you
feel comfortable contributing. In fact, if you encounter any confusion
or frustration during the contribution process, please create a GitHub
issue and we'll do our best to improve the process.

## Getting in touch

Discussion around http4s is currently happening in the [Gitter room] as
well as on Github issue and PR pages.

Feel free to open an issue if you notice a bug, have an idea for a
feature, or have a question about the code. Pull requests are also
gladly accepted.

People are expected to follow the [Typelevel Code of Conduct] when
discussing http4s on the Github page, Gitter channel, or other venues.

We hope that our community will be respectful, helpful, and kind. If
you find yourself embroiled in a situation that becomes heated, or
that fails to live up to our expectations, you should disengage and
contact one of the [community staff] in private. We hope to avoid
letting minor aggressions and misunderstandings escalate into larger
problems.

If you are being harassed, please contact one of the [community staff]
immediately so that we can support you.

## Acknowledgements

This document is heavily based on the [Cats contributor's guide].

[fork and pull]: https://help.github.com/articles/using-pull-requests/
[issues page]: https://github.com/http4s/http4s/issues
[create a GitHub issue]: https://github.com/http4s/http4s/issues/new
[sbt installed]: http://www.scala-sbt.org/0.13/tutorial/Setup.html
[codecov.io]: https://codecov.io/gh/http4s/http4s
[http4s.org]: http://http4s.org/
[docs subproject]: https://github.com/http4s/http4s/tree/master/docs
[Gitter room]: http://gitter.im/http4s/http4s
[Typelevel Code of Conduct]: http://typelevel.org/conduct.html
[community staff]: http://http4s.org/community/conduct.html#community-staff
[Apache License 2.0]: https://github.com/http4s/http4s/blob/master/LICENSE
[Cats contributor's guide]: https://github.com/typelevel/cats/blob/master/CONTRIBUTING.md
