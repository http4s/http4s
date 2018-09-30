# mime-http4s-generator

This is a small utility to take the latest MimeDB json file and produce source code to be used by http4s.
To use it go into sbt and call

```
sbt
mimedb-generator/run core/src/main/scala/org/http4s
```

That will produce a file called `MimeDB.scala` on the `core` project generated from the latest version of the Mime database
Note that the `MimeDb.scala` is not formatted with `scalafmt`. You need to format it before committing
