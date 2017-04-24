package org.http4s
package circe

import fs2.Chunk
import io.circe.{Encoder, Decoder, Json, Printer}
import io.circe.jawn.CirceSupportParser.facade
import org.http4s.batteries._
import org.http4s.headers.`Content-Type`
import org.http4s.util.ByteVectorChunk
import scodec.bits.ByteVector

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
    EntityEncoder[Chunk[Byte]].contramap[Json] { json =>
      val bytes = printer.prettyByteBuffer(json)
      ByteVectorChunk(ByteVector.view(bytes))
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[A](implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEncoderWithPrinterOf(defaultPrinter)

  def jsonEncoderWithPrinterOf[A](printer: Printer)(implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEncoderWithPrinter(printer).contramap[A](encoder.apply)

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
