package org.http4s
package jawn

trait JawnDecodeSupportSpec[J] extends Http4sSpec with JawnInstances {
  def testJsonDecoder(decoder: EntityDecoder[J]) {
    "json decoder" should {
      "return right when the entity is valid" in {
        val resp = Response(Status.Ok).withBody("""{"valid": true}""").run
        decoder.decode(resp, strict = false).run.run must be_\/-
      }

      "return a ParseFailure when the entity is invalid" in {
        val resp = Response(Status.Ok).withBody("""garbage""").run
        decoder.decode(resp, strict = false).run.run must be_-\/.like {
          case ParseFailure("Invalid JSON", _) => ok
        }
      }

      "return a ParseFailure when the entity is empty" in {
        val resp = Response(Status.Ok).withBody("").run
        decoder.decode(resp, strict = false).run.run must be_-\/.like {
          case ParseFailure("Invalid JSON", _) => ok
        }
      }
    }
  }
}
