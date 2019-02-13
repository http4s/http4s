package org.http4s
package jawn

import cats.effect.IO
import org.specs2.matcher.MatchResult

trait JawnDecodeSupportSpec[J] extends Http4sSpec {
  def testJsonDecoder(decoder: EntityDecoder[IO, J]) =
    "json decoder" should {
      "return right when the entity is valid" in {
        val resp = Response[IO](Status.Ok).withEntity("""{"valid": true}""")
        decoder.decode(resp, strict = false).value.unsafeRunSync must beRight
      }

      testErrors(decoder)(
        emptyBody = {
          case MalformedMessageBodyFailure("Invalid JSON: empty body", _) => ok
        },
        parseError = {
          case MalformedMessageBodyFailure("Invalid JSON", _) => ok
        }
      )
    }

  def testJsonDecoderError(decoder: EntityDecoder[IO, J])(
      emptyBody: PartialFunction[DecodeFailure, MatchResult[Any]],
      parseError: PartialFunction[DecodeFailure, MatchResult[Any]]
  ) =
    "json decoder with custom errors" should {
      testErrors(decoder)(emptyBody = emptyBody, parseError = parseError)
    }

  private def testErrors(decoder: EntityDecoder[IO, J])(
      emptyBody: PartialFunction[DecodeFailure, MatchResult[Any]],
      parseError: PartialFunction[DecodeFailure, MatchResult[Any]]
  ) = {
    "return a ParseFailure when the entity is invalid" in {
      val resp = Response[IO](Status.Ok).withEntity("""garbage""")
      decoder.decode(resp, strict = false).value.unsafeRunSync must beLeft.like(parseError)
    }

    "return a ParseFailure when the entity is empty" in {
      val resp = Response[IO](Status.Ok).withEntity("")
      decoder.decode(resp, strict = false).value.unsafeRunSync must beLeft.like(emptyBody)
    }
  }
}
