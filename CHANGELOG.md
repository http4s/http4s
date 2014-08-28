# v0.3.0 (2014-08-29)

* Major under the covers changes for response generators.
* Removed exception throwing methods for the construction of Uri and QValue and 
  other similar methods in favor of disjunction and macro enforced literals.
* Refactored the Http Message types to be more flexible to other frameworks 
  syntax needs.
* Refactored imports to match the structure followed by [scalaz](https://github.com/scalaz/scalaz).
* Upgrade to scalaz-7.1.0 and scalaz-stream 0.5a
* Numerous changes to HTTP values.
* Add EntityDecoders.
* Add json support through the [jawn](https://github.com/non/jawn) library
* New Writable structure.

# v0.2.0 (2014-07-15)

* Scala 2.11 support
* Spun off http4s-server module. http4s-core is neutral between server and
  the future client.
* New builder for running Blaze, Jetty, and Tomcat servers.
* Configurable timeouts in each server backend.
* Replace Chunk with scodec.bits.ByteVector.
* Many enhancements and bugfixes to URI type.
* Drop joda-time dependency for slimmer date-time class.
* Capitalized method names in http4s-dsl.

# v0.1.0 (2014-04-15)

* Initial public release.
