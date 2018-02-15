package org.http4s
package circe

import cats._
import cats.effect._
import cats.implicits._
import fs2.Chunk
import fs2.interop.scodec.ByteVectorChunk
import io.circe.{Decoder, Encoder, Json, Printer}
import io.circe.jawn._
import io.circe.jawn.CirceSupportParser.facade
import java.nio.ByteBuffer
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

trait CirceInstances {
  def jsonDecoderIncremental[F[_]: Sync]: EntityDecoder[F, Json] =
    jawn.jawnDecoder[F, Json]

  def jsonDecoderByteBuffer[F[_]: Sync]: EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(MediaType.`application/json`)(jsonDecoderByteBufferImpl[F])

  private def jsonDecoderByteBufferImpl[F[_]: Sync](msg: Message[F]): DecodeResult[F, Json] =
    EntityDecoder.collectBinary(msg).flatMap { segment =>
      val bb = ByteBuffer.wrap(segment.force.toArray)
      if (bb.hasRemaining) {
        parseByteBuffer(bb) match {
          case Right(json) =>
            DecodeResult.success[F, Json](json)
          case Left(pf) =>
            DecodeResult.failure[F, Json](
              MalformedMessageBodyFailure("Invalid JSON", Some(pf.underlying)))
        }
      } else {
        DecodeResult.failure[F, Json](MalformedMessageBodyFailure("Invalid JSON: empty body", None))
      }
    }

  implicit def jsonDecoder[F[_]: Sync]: EntityDecoder[F, Json]

  def jsonDecoderAdaptive[F[_]: Sync](cutoff: Long): EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(MediaType.`application/json`) { msg =>
      msg.contentLength match {
        case Some(contentLength) if contentLength < cutoff =>
          jsonDecoderByteBufferImpl[F](msg)
        case _ => jawn.jawnDecoderImpl[F, Json](msg)
      }
    }

  def jsonOf[F[_]: Sync, A](implicit decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      decoder
        .decodeJson(json)
        .fold(
          failure =>
            DecodeResult.failure(
              InvalidMessageBodyFailure(s"Could not decode JSON: $json", Some(failure))),
          DecodeResult.success(_)
        )
    }

  /**
    * An [[EntityDecoder]] that uses circe's accumulating decoder for decoding the JSON.
    *
    * In case of a failure, returns an [[InvalidMessageBodyFailure]] with the cause containing
    * a [[DecodingFailures]] exception, from which the errors can be extracted.
    */
  def accumulatingJsonOf[F[_]: Sync, A](implicit decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      decoder
        .accumulating(json.hcursor)
        .fold(
          failures =>
            DecodeResult.failure(
              InvalidMessageBodyFailure(
                s"Could not decode JSON: $json",
                Some(DecodingFailures(failures)))),
          DecodeResult.success(_)
        )
    }

  protected def defaultPrinter: Printer

  implicit def jsonEncoder[F[_]: EntityEncoder[?[_], String]: Applicative]: EntityEncoder[F, Json] =
    jsonEncoderWithPrinter(defaultPrinter)

  def jsonEncoderWithPrinter[F[_]: EntityEncoder[?[_], String]: Applicative](
      printer: Printer): EntityEncoder[F, Json] =
    EntityEncoder[F, Chunk[Byte]]
      .contramap[Json] { json =>
        val bytes = printer.prettyByteBuffer(json)
        ByteVectorChunk(ByteVector.view(bytes))
      }
      .withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[F[_]: EntityEncoder[?[_], String]: Applicative, A](
      implicit encoder: Encoder[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinterOf(defaultPrinter)

  def jsonEncoderWithPrinterOf[F[_]: EntityEncoder[?[_], String]: Applicative, A](printer: Printer)(
      implicit encoder: Encoder[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinter[F](printer).contramap[A](encoder.apply)

  implicit val encodeUri: Encoder[Uri] =
    Encoder.encodeString.contramap[Uri](_.toString)

  implicit val decodeUri: Decoder[Uri] =
    Decoder.decodeString.emap { str =>
      Uri.fromString(str).leftMap(_ => "Uri")
    }

  implicit class MessageSyntax[F[_]: Sync](self: Message[F]) {
    def decodeJson[A](implicit decoder: Decoder[A]): F[A] =
      self.as(implicitly, jsonOf[F, A])
  }
}

object CirceInstances {
  def withPrinter(p: Printer): CirceInstances =
    new CirceInstances {
      val defaultPrinter: Printer = p
      def jsonDecoder[F[_]: Sync]: EntityDecoder[F, Json] = defaultJsonDecoder
    }

  // default cutoff value is based on benchmarks results
  def defaultJsonDecoder[F[_]: Sync]: EntityDecoder[F, Json] =
    jsonDecoderAdaptive(cutoff = 100000)
}
