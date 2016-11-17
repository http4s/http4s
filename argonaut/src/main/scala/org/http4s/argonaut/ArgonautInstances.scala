package org.http4s
package argonaut

import _root_.argonaut.{DecodeResult => _, _}
import org.http4s.headers.`Content-Type`

trait ArgonautInstances {
  implicit val jsonDecoder: EntityDecoder[Json] = jawn.jawnDecoder(Parser.facade)

  def jsonOf[A](implicit decoder: DecodeJson[A]): EntityDecoder[A] =
    jsonDecoder.flatMapR { json =>
      decoder.decodeJson(json).fold(
        (message, history) =>
          DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json, error: $message, cursor: $history")),
        DecodeResult.success(_)
      )
    }

  def jsonEncoder(prettyParams: PrettyParams): EntityEncoder[Json] =
    EntityEncoder.stringEncoder(Charset.`UTF-8`).contramap[Json] { json =>
      // TODO naive implementation materializes to a String.
      // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
      prettyParams.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))

  implicit val jsonEncoder: EntityEncoder[Json] =
    jsonEncoder(Argonaut.nospace)

  def jsonEncoderOf[A](prettyParams: PrettyParams)(implicit encoder: EncodeJson[A]): EntityEncoder[A] =
    jsonEncoder(prettyParams).contramap[A](encoder.encode)

  def jsonEncoderOf[A](implicit encoder: EncodeJson[A]): EntityEncoder[A] =
    jsonEncoderOf(Argonaut.nospace)
}
