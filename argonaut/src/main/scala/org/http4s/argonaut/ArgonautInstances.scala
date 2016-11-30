package org.http4s
package argonaut

import _root_.argonaut.{DecodeResult => ArgDecodeResult, _}, Argonaut._
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

  protected def defaultPrettyParams: PrettyParams

  implicit def jsonEncoder: EntityEncoder[Json] =
    jsonEncoderWithPrettyParams(defaultPrettyParams)

  def jsonEncoderWithPrettyParams(prettyParams: PrettyParams): EntityEncoder[Json] =
    EntityEncoder.stringEncoder(Charset.`UTF-8`).contramap[Json] { json =>
      prettyParams.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))

  def jsonEncoderOf[A](implicit encoder: EncodeJson[A]): EntityEncoder[A] =
    jsonEncoderWithPrinterOf(defaultPrettyParams)

  def jsonEncoderWithPrinterOf[A](prettyParams: PrettyParams)
    (implicit encoder: EncodeJson[A]): EntityEncoder[A] =
    jsonEncoderWithPrettyParams(prettyParams).contramap[A](encoder.encode)

  implicit val urlCodec: CodecJson[Uri] = CodecJson(
    (uri: Uri) => Json.jString(uri.toString),
    c => c.as[String]
      .flatMap({str =>
        Uri.fromString(str)
          .fold(err => ArgDecodeResult.fail(err.toString, c.history), ArgDecodeResult.ok)
      })
  )
}

object ArgonautInstances {
  def withPrettyParams(pp: PrettyParams): ArgonautInstances = {
    new ArgonautInstances {
      def defaultPrettyParams: PrettyParams = pp
    }
  }
}
