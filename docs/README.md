[http4s.org](http://http4s.org/) is generated from this directory.
The site is published by Travis to the gh-pages branch of this
repository.

* src/site is a basic Jekyll site that contains mostly static
  information about the project as a whole, and does not pertain to a
  particular version.  Travis only updates this on the master branch.

* src/main/tut is a collection of compiler-verified documentation
  built on [tut](http://github.com/tpolecat/tut).  It is a deeper
  walkthrough of the library, and is versioned to remain in sync with
  the code.  These tutorials are published to the [tutdir](docs/x.y)
  directory of the site.  Travis updates this on the master branch,
  and any branch named release-*.

* Scaladoc is aggregated by the unidoc plugin and published to
  [api](api/x.y).

[tutdir]: http://http4s.org/docs/
[api]: http://http4s.org/api/

## Previewing the site

You can generate the site with:

```
sbt ++2.11.7 docs/makeSite
```

The site can be previewed locally by running the bin/local-site script
from the root of this repository.  Navigate to [http://0.0.0.0:4000/].
