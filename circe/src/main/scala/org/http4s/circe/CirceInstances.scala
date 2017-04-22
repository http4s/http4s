package org.http4s
package circe

import cats._
import cats.implicits._
import fs2.util.Catchable
import io.circe.{Encoder, Decoder, Json, Printer}
import io.circe.jawn.CirceSupportParser.facade
import org.http4s.headers.`Content-Type`

// Originally based on ArgonautInstances
trait CirceInstances {
  implicit def jsonDecoder[F[_]: Catchable: Applicative]: EntityDecoder[F, Json] =
    jawn.jawnDecoder

  def jsonOf[F[_], A](implicit C: Catchable[F], F: Monad[F], decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      decoder.decodeJson(json).fold(
        failure =>
          DecodeResult.failure[F, A](InvalidMessageBodyFailure(s"Could not decode JSON: $json", Some(failure))),
        DecodeResult.success[F, A](_)
      )
    }

  protected def defaultPrinter: Printer

  implicit def jsonEncoder[F[_]: EntityEncoder[?[_], String]]: EntityEncoder[F, Json] =
    jsonEncoderWithPrinter(defaultPrinter)

  def jsonEncoderWithPrinter[F[_]: EntityEncoder[?[_], String]](printer: Printer): EntityEncoder[F, Json] =
    EntityEncoder[F, String].contramap[Json] { json =>
      // Comment from ArgonautInstances (which this code is based on):
      // TODO naive implementation materializes to a String.
      // See https://github.com/non/jawn/issues/6#issuecomment-65018736
      printer.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[F[_]: EntityEncoder[?[_], String], A](implicit encoder: Encoder[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinterOf(defaultPrinter)

  def jsonEncoderWithPrinterOf[F[_]: EntityEncoder[?[_], String], A](printer: Printer)(implicit encoder: Encoder[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinter[F](printer).contramap[A](encoder.apply)

  implicit val encodeUri: Encoder[Uri] =
    Encoder.encodeString.contramap[Uri](_.toString)

  implicit val decodeUri: Decoder[Uri] =
    Decoder.decodeString.emap { str =>
      Uri.fromString(str).leftMap(_ => "Uri")
    }
}

object CirceInstances {
  def withPrinter(p: Printer): CirceInstances = {
    new CirceInstances {
      def defaultPrinter: Printer = p
    }
  }
}
