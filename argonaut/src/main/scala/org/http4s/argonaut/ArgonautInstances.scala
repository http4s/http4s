package org.http4s
package argonaut

import _root_.argonaut.{EncodeJson, DecodeJson, Argonaut, Json}
import org.http4s.headers.`Content-Type`

trait ArgonautInstances {
  implicit val json: EntityDecoder[Json] = jawn.jawnDecoder(Parser.facade)

  def jsonOf[A](implicit decoder: DecodeJson[A]): EntityDecoder[A] =
    json.flatMapR { json =>
      decoder.decodeJson(json).fold(
        (message, history) =>
          DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json, error: $message, cursor: $history")),
        DecodeResult.success(_)
      )
    }

  implicit val jsonEncoder: EntityEncoder[Json] =
    EntityEncoder.stringEncoder(Charset.`UTF-8`).contramap[Json] { json =>
      // TODO naive implementation materializes to a String.
      // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
      Argonaut.nospace.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))

  def jsonEncoderOf[A](implicit encoder: EncodeJson[A]): EntityEncoder[A] =
    jsonEncoder.contramap[A](encoder.encode)
}
