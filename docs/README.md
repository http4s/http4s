[http4s.org](http://http4s.org/) is generated from this directory.
The site is published by Travis to the gh-pages branch of this
repository.

* src/hugo is a basic Hugo site that contains mostly static
  information about the project as a whole, and does not pertain to a
  particular version.  Travis only updates this on the master branch.

* src/main/tut is a collection of compiler-verified documentation
  built on [tut](http://github.com/tpolecat/tut).  It is a deeper
  walkthrough of the library, and is versioned to remain in sync with
  the code.  These tutorials are published to the `/vX.Y` for version
  X.Y.Z of http4s.  Travis updates this on the master branch, and any
  branch named vX.Y.

* Scaladoc is aggregated by the unidoc plugin and published to
  `/vX.Y/api`.

[tutdir]: http://http4s.org/docs/

## Previewing the site

You can generate the site with:

```
sbt docs/makeSite
```

The site can be previewed locally by running the bin/local-site script
from the root of this repository.  Navigate to [http://127.0.0.1:4000/].
