package org.http4s
package circe

import io.circe.{Decoder, Encoder, Json, Printer}
import io.circe.jawn.CirceSupportParser.facade
import org.http4s.headers.`Content-Type`

import scalaz.Show

// Originally based on ArgonautInstances
trait CirceInstances {
  implicit lazy val jsonDecoder: EntityDecoder[Json] = jawn.jawnDecoder(facade)

  def jsonOf[A](implicit decoder: Decoder[A]): EntityDecoder[A] =
    jsonDecoder.flatMapR { json =>
      decoder.decodeJson(json).fold(
        failure =>
          DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json", Some(failure))),
        DecodeResult.success(_)
      )
    }

  implicit def jsonEncoder(implicit printer: Printer = Printer.noSpaces): EntityEncoder[Json] =
    EntityEncoder.showEncoder[String](DefaultCharset, Show.showFromToString[String]).contramap[Json](printer.pretty)
      .withContentType(`Content-Type`(MediaType.`application/json`))

  implicit def jsonEncoderOf[A](implicit encoder: Encoder[A], printer: Printer = Printer.noSpaces): EntityEncoder[A] =
    jsonEncoder.contramap[A](encoder.apply)
}
