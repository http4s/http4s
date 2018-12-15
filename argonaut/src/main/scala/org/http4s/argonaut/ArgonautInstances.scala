package org.http4s
package argonaut

import _root_.argonaut.{DecodeResult => ArgDecodeResult, _}
import _root_.argonaut.Argonaut._
import cats.Applicative
import cats.effect.Sync
import org.http4s.argonaut.Parser.facade
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnInstances

trait ArgonautInstances extends JawnInstances {
  implicit def jsonDecoder[F[_]: Sync]: EntityDecoder[F, Json] =
    jawnDecoder

  def jsonOf[F[_]: Sync, A](implicit decoder: DecodeJson[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      decoder
        .decodeJson(json)
        .fold(
          (message, history) =>
            DecodeResult.failure(
              InvalidMessageBodyFailure(
                s"Could not decode JSON: $json, error: $message, cursor: $history")),
          DecodeResult.success(_)
        )
    }

  protected def defaultPrettyParams: PrettyParams

  implicit def jsonEncoder[F[_]: Applicative]: EntityEncoder[F, Json] =
    jsonEncoderWithPrettyParams[F](defaultPrettyParams)

  def jsonEncoderWithPrettyParams[F[_]](prettyParams: PrettyParams): EntityEncoder[F, Json] =
    EntityEncoder
      .stringEncoder(Charset.`UTF-8`)
      .contramap[Json](prettyParams.pretty)
      .withContentType(`Content-Type`(MediaType.application.json))

  def jsonEncoderOf[F[_]: Applicative, A](implicit encoder: EncodeJson[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinterOf(defaultPrettyParams)

  def jsonEncoderWithPrinterOf[F[_]: Applicative, A](prettyParams: PrettyParams)(
      implicit encoder: EncodeJson[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrettyParams[F](prettyParams).contramap[A](encoder.encode)

  implicit val uriCodec: CodecJson[Uri] = CodecJson(
    (uri: Uri) => Json.jString(uri.toString),
    c =>
      c.as[String]
        .flatMap(
          str =>
            Uri
              .fromString(str)
              .fold(err => ArgDecodeResult.fail(err.toString, c.history), ArgDecodeResult.ok))
  )

  implicit class MessageSyntax[F[_]: Sync](self: Message[F]) {
    def decodeJson[A](implicit decoder: DecodeJson[A]): F[A] =
      self.as(implicitly, jsonOf[F, A])
  }
}

object ArgonautInstances {
  def withPrettyParams(pp: PrettyParams): ArgonautInstances =
    new ArgonautInstances {
      def defaultPrettyParams: PrettyParams = pp
    }
}
