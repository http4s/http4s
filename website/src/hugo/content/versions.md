---
menu: main
weight: 100
title: Versions
---

## Roadmap

* [`http4s-0.16`](../v0.16/) is the final release series based on [Scalaz] and [scalaz-stream]. We will support this branch with bugfixes, but not new development.
* [`http4s-0.17`](../v0.17/) is the first release on [Cats] and [FS2].  Scalaz users are encouraged to look at [shims].
* [`http4s-0.18`](../v0.18/) is the first release on [cats-effect].  This release parameterizes the effect, and is intended to work well with `cats.effect.IO`, `monix.eval.Task`, `scalaz.concurrent.Task`, and any other effect type with a `cats.effect.Effect` instance.

[Scalaz]: https://github.com/scalaz/scalaz
[scalaz-stream]: https://github.com/scalaz/scalaz-stream
[Cats]: https://typelevel.org/cats/
[FS2]: https://github.com/functional-streams-for-scala/fs2
[cats-effect]: https://github.com/typelevel/cats-effect
[shims]: https://github.com/djspiewak/shims

## Matrix
      
* <span class="badge badge-success">Stable</span> releases are
  recommended for production use and are maintained with
  backward, binary compatible bugfixes from the http4s
  team.
* <span class="badge badge-warning">Milestone</span> releases
  are published for early adopters.  API breakage may still occur
  until the first stable release in that series.
* <span class="badge badge-secondary">EOL</span> releases are
  no longer actively maintained, but pull requests with a tale
  of woe may be considered.
* Snapshots of all branches are published automatically by [Travis CI]
  to the [Sonatype Snapshot repo].

[Travis CI]: https://travis-ci.org/http4s/http4s
[Sonatype Snapshot repo]: https://oss.sonatype.org/content/repositories/snapshots/org/http4s/

<table class="table table-responsive table-hover">
  <thead>
    <tr>
      <th>http4s</th>
      <th class="text-center">Status</th>
      <th class="text-center">Scala 2.10</th>
      <th class="text-center">Scala 2.11</th>
      <th class="text-center">Scala 2.12</th>
      <th>FP</th>
      <th>Streaming</th>
      <th>JDK</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><a href="/v0.18">0.18.0&#8209;M1</a></td>
      <td class="text-center"><span class="badge badge-warning">Milestone</span></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td>cats&#8209;1.0.0&#8209;MF</td>
      <td>fs2&#8209;0.10.0-M6</td>
      <td>1.8+</td>
    </tr>
    <tr>
      <td><a href="/v0.17">0.17.0</a></td>
      <td class="text-center"><span class="badge badge-success">Stable</span></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td>cats&#8209;0.9</td>
      <td>fs2&#8209;0.9</td>
      <td>1.8+</td>
    </tr>
    <tr>
      <td><a href="/v0.16">0.16.0a</a></td>
      <td class="text-center"><span class="badge badge-success">Stable</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td>scalaz&#8209;7.2</td>
      <td>scalaz&#8209;stream&#8209;0.8a</td>
      <td>1.8+</td>
    </tr>
    <tr>
      <td><a href="/v0.16">0.16.0</a></td>
      <td class="text-center"><span class="badge badge-success">Stable</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.8</td>
      <td>1.8+</td>
    </tr>
    <tr>
      <td><a href="/v0.15">0.15.16a</a></td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td>scalaz&#8209;7.2</td>
      <td>scalaz&#8209;stream&#8209;0.8a</td>
      <td>1.8+</td>
    </tr>
    <tr>
      <td><a href="/v0.15">0.15.16</a></td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.8</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.14.11a</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.2</td>
      <td>scalaz&#8209;stream&#8209;0.8a</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.14.11</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.8</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.13.3a</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.2</td>
      <td>scalaz&#8209;stream&#8209;0.8a</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.13.3</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.8</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.12.6</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.8</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.11.3</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.8</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.10.1</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.7a</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.9.3</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.7a</td>
      <td>1.8+</td>
    </tr>
  </tbody>
  <tbody>
    <tr>
      <td>0.8.6</td>
      <td class="text-center"><span class="badge badge-secondary">EOL</span></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-check"></i></td>
      <td class="text-center"><i class="fa fa-ban"></i></td>
      <td>scalaz&#8209;7.1</td>
      <td>scalaz&#8209;stream&#8209;0.7a</td>
      <td>1.7+</td>
    </tr>
  </tbody>
</table>
