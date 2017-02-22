# Contributor guide

http4s is an open source project, and depends on its users to continue
to improve.  We are thrilled that you are interested in helping!

## Submitting pull requests

### Claim an issue

If there is already a GitHub issue for the task you are working on,
please leave a comment to let people know that you are working on it.
If there isn't already an issue and it is a non-trivial task, it's a
good idea to create one (and note that you're working on it). This
prevents contributors from duplicating effort.

### Targeting a branch

http4s actively maintains three branches:

* [release-0.15.x]: the current production release.  Merges to [master].
* [master]: the current development mainline.  Merges to [cats].
* [cats]: the [cats-fs2] port

The guide below helps find the most appropriate branch for your change.

My change is...                               | Branch
----------------------------------------------|-------------------
Documentation of existing features            | [release-0.15.x]
Documentation of unreleased features          | [master]
Change to docs/src/site                       | [master]
Specific to cats or fs2                       | [cats]
Binary compatible with current release        | [release-0.15.x]
Binary incompatible with current release      | [master]

Still unsure?  Don't worry!  Send us that PR, and we'll cherry-pick it
to the right place.

### Attributions

If your contribution has been derived from or inspired by other work,
please state this in its scaladoc comment and provide proper
attribution. When possible, include the original authors' names and a
link to the original work.

### Grant of license

http4s is licensed under the [Apache License 2.0]. Opening a pull
request signifies your consent to license your contributions under the
Apache License 2.0.

## Submitting issues

If you notice a bug, have an idea for a feature, or have a question
about the code, please don't hesitate to [create a GitHub issue].
Many of the good ideas in http4s were introduced by one user and later
implemented by another.

## Building the Community

### Join the adopters list

It's easy to [add yourself as an adopter].  You get free advertising
on the [adopters list], and a robust list encourages gives new users
the confidence to try http4s and grows the community for all.

## Spread the word

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

[issues page]: https://github.com/http4s/http4s/issues
[create a GitHub issue]: https://github.com/http4s/http4s/issues/new
[Github issues]: https://github.com/http4s/http4s/issues
[http4s.org]: http://http4s.org/
[adopters list]: http://http4s.org/adopters/
[add yourself as an adopter]: https://github.com/http4s/http4s/edit/master/docs/src/hugo/content/adopters.md
[Gitter]: http://gitter.im/http4s/http4s
[Typelevel Code of Conduct]: http://typelevel.org/conduct.html
[community staff]: http://http4s.org/community/conduct.html#community-staff
[Apache License 2.0]: https://github.com/http4s/http4s/blob/master/LICENSE
[Cats contributor's guide]: https://github.com/typelevel/cats/blob/master/CONTRIBUTING.md

[release-0.15.x]: https://github.com/http4s/http4s/tree/release-0.15.x
[master]: https://github.com/http4s/http4s/tree/master
[cats]: https://github.com/http4s/http4s/tree/cats
