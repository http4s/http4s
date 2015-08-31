package org.http4s
package circe

import io.circe.{Encoder, Decoder, Json, Printer}
import io.circe.jawn.CirceSupportParser.facade
import org.http4s.headers.`Content-Type`

// Originally based on ArgonautInstances
trait CirceInstances {
  implicit val json: EntityDecoder[Json] = jawn.jawnDecoder(facade)

  def jsonOf[A](implicit decoder: Decoder[A]): EntityDecoder[A] =
    json.flatMapR { json =>
      decoder.decodeJson(json).fold(
        failure =>
          DecodeResult.failure(ParseFailure(
            "Could not decode JSON",
            s"json: $json, error: ${failure.message}, cursor: ${failure.history}"
          )),
        DecodeResult.success(_)
      )
    }

  implicit val jsonEncoder: EntityEncoder[Json] =
    EntityEncoder[String].contramap[Json] { json =>
      // Comment from ArgonautInstances (which this code is based on):
      // TODO naive implementation materializes to a String.
      // See https://github.com/non/jawn/issues/6#issuecomment-65018736
      Printer.noSpaces.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[A](implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEncoder.contramap[A](encoder.apply)
}
