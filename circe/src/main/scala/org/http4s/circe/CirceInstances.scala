package org.http4s
package circe

import io.circe.{Encoder, Decoder, Json, Printer}
import io.circe.jawn.CirceSupportParser.facade
import org.http4s.headers.`Content-Type`

// Originally based on ArgonautInstances
trait CirceInstances {
  implicit val jsonDecoder: EntityDecoder[Json] = jawn.jawnDecoder(facade)

  def jsonOf[A](implicit decoder: Decoder[A]): EntityDecoder[A] =
    jsonDecoder.flatMapR { json =>
      decoder.decodeJson(json).fold(
        failure =>
          DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json", Some(failure))),
        DecodeResult.success(_)
      )
    }

  protected def defaultPrinter: Printer

  implicit def jsonEncoder: EntityEncoder[Json] =
    jsonEncoderWithPrinter(defaultPrinter)

  def jsonEncoderWithPrinter(printer: Printer): EntityEncoder[Json] =
    EntityEncoder[String].contramap[Json] { json =>
      // Comment from ArgonautInstances (which this code is based on):
      // TODO naive implementation materializes to a String.
      // See https://github.com/non/jawn/issues/6#issuecomment-65018736
      printer.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[A](implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEncoderWithPrinterOf(defaultPrinter)

  def jsonEncoderWithPrinterOf[A](printer: Printer)(implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEncoderWithPrinter(printer).contramap[A](encoder.apply)

  implicit val encodeUri: Encoder[Uri] =
    Encoder.encodeString.contramap[Uri](_.toString)

  implicit val decodeUri: Decoder[Uri] =
    Decoder.decodeString.emap { str =>
      Uri.fromString(str).leftMap(_ => "Uri").toEither
    }
}

object CirceInstances {
  def withPrinter(p: Printer): CirceInstances = {
    new CirceInstances {
      def defaultPrinter: Printer = p
    }
  }
}
