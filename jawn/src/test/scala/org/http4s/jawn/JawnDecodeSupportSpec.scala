package org.http4s
package jawn

trait JawnDecodeSupportSpec[J] extends Http4sSpec with JawnInstances {
  def testJsonDecoder(decoder: EntityDecoder[J]) =
    "json decoder" should {
      "return right when the entity is valid" in {
        val resp = Response(Status.Ok).withBody("""{"valid": true}""").unsafeRun
        decoder.decode(resp, strict = false).value.unsafeRun must beRight
      }

      "return a ParseFailure when the entity is invalid" in {
        val resp = Response(Status.Ok).withBody("""garbage""").unsafeRun
        decoder.decode(resp, strict = false).value.unsafeRun must beLeft.like {
          case MalformedMessageBodyFailure("Invalid JSON", _) => ok
        }
      }

      "return a ParseFailure when the entity is empty" in {
        val resp = Response(Status.Ok).withBody("").unsafeRun
        decoder.decode(resp, strict = false).value.unsafeRun must beLeft.like {
          case MalformedMessageBodyFailure("Invalid JSON: empty body", _) => ok
        }
      }
    }
}
