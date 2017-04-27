package org.http4s
package circe

import java.nio.ByteBuffer
import cats.syntax.either._
import fs2.{Chunk, Task}
import io.circe.{Encoder, Decoder, Json, Printer}
import io.circe.jawn._
import io.circe.jawn.CirceSupportParser.facade
import org.http4s.headers.`Content-Type`
import org.http4s.util.ByteVectorChunk
import scodec.bits.ByteVector

trait CirceInstances {
  val jsonDecoderIncremental: EntityDecoder[Json] = jawn.jawnDecoder(facade)

  val jsonDecoderByteBuffer: EntityDecoder[Json] =
    EntityDecoder.decodeBy(MediaType.`application/json`)(
      jsonDecoderByteBufferImpl)

  private def jsonDecoderByteBufferImpl(msg: Message): DecodeResult[Json] =
    EntityDecoder.collectBinary(msg).flatMap { chunk =>
      val bb = ByteBuffer.wrap(chunk.toBytes.values)
      if (bb.hasRemaining) {
        parseByteBuffer(bb) match {
          case Right(json) =>
            DecodeResult.success(Task.now(json))
          case Left(pf) =>
            DecodeResult.failure(MalformedMessageBodyFailure(
              s"Invalid JSON", Some(pf.underlying)))
        }
      } else {
        DecodeResult.failure(MalformedMessageBodyFailure(
          "Invalid JSON: empty body", None))
      }
    }

  implicit def jsonDecoder: EntityDecoder[Json]

  def jsonDecoderAdaptive(cutoff: Long): EntityDecoder[Json] =
    EntityDecoder.decodeBy(MediaType.`application/json`) { msg =>
      msg.contentLength match {
        case Some(contentLength) if contentLength < cutoff =>
          jsonDecoderByteBufferImpl(msg)
        case _ => jawn.jawnDecoderImpl(msg)(facade)
      }
    }

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
      val defaultPrinter: Printer = p
      val jsonDecoder: EntityDecoder[Json] = defaultJsonDecoder
    }
  }

  // default cutoff value is based on benchmarks results
  val defaultJsonDecoder: EntityDecoder[Json] =
    jsonDecoderAdaptive(cutoff = 100000)
}
