[http4s.org] is generated from this directory.  The site is published
by Travis to the gh-pages branch of this repository.

* src/main/tut is a collection of compiler-verified documentation
  built on [tut].
  
* Scaladoc is aggregated by the unidoc plugin and published to
  [api].

## Previewing the site

You can generate the site with:

```
sbt docs/makeMicrosite
```

In a separate terminal, run:

```
cd docs/target/site
jekyll serve --watch
```

The site is viewable at [http://127.0.0.1:4000/], and will regenerate
on subsequent runs of `docs/makeMicrosite`.

[http4s.org]: http://http4s.org/
[tut]: http://github.com/tpolecat/tut
[tutdir]: http://http4s.org/docs/
[api]: http://http4s.org/api/
