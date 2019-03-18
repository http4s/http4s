package org.http4s
package circe

import java.nio.ByteBuffer

import cats._
import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import fs2.Chunk
import io.circe._
import io.circe.jawn.CirceSupportParser.facade
import io.circe.jawn._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnInstances
import org.typelevel.jawn.ParseException

trait CirceInstances extends JawnInstances {
  protected def defaultPrinter: Printer = Printer.noSpaces

  protected def circeParseExceptionMessage: ParsingFailure => DecodeFailure =
    CirceInstances.defaultCirceParseError

  protected def jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
    CirceInstances.defaultJsonDecodeError

  def jsonDecoderIncremental[F[_]: Sync]: EntityDecoder[F, Json] =
    this.jawnDecoder[F, Json]

  def jsonDecoderByteBuffer[F[_]: Sync]: EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(MediaType.application.json)(jsonDecoderByteBufferImpl[F])

  private def jsonDecoderByteBufferImpl[F[_]: Sync](msg: Message[F]): DecodeResult[F, Json] =
    EntityDecoder.collectBinary(msg).flatMap { chunk =>
      val bb = ByteBuffer.wrap(chunk.toArray)
      if (bb.hasRemaining) {
        parseByteBuffer(bb) match {
          case Right(json) =>
            DecodeResult.success[F, Json](json)
          case Left(pf) =>
            DecodeResult.failure[F, Json](circeParseExceptionMessage(pf))
        }
      } else {
        DecodeResult.failure[F, Json](jawnEmptyBodyMessage)
      }
    }

  // default cutoff value is based on benchmarks results
  implicit def jsonDecoder[F[_]: Sync]: EntityDecoder[F, Json] =
    jsonDecoderAdaptive(cutoff = 100000, MediaType.application.json)

  def jsonDecoderAdaptive[F[_]: Sync](
      cutoff: Long,
      r1: MediaRange,
      rs: MediaRange*): EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(r1, rs: _*) { msg =>
      msg.contentLength match {
        case Some(contentLength) if contentLength < cutoff =>
          jsonDecoderByteBufferImpl[F](msg)
        case _ => this.jawnDecoderImpl[F, Json](msg)
      }
    }

  def jsonOf[F[_]: Sync, A](implicit decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonOfWithMedia(MediaType.application.json)

  def jsonOfWithMedia[F[_]: Sync, A](r1: MediaRange, rs: MediaRange*)(
      implicit decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonDecoderAdaptive[F](cutoff = 100000, r1, rs: _*).flatMapR { json =>
      decoder
        .decodeJson(json)
        .fold(
          failure => DecodeResult.failure(jsonDecodeError(json, NonEmptyList.one(failure))),
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
          failures => DecodeResult.failure(jsonDecodeError(json, failures)),
          DecodeResult.success(_)
        )
    }

  implicit def jsonEncoder[F[_]: Applicative]: EntityEncoder[F, Json] =
    jsonEncoderWithPrinter(defaultPrinter)

  def jsonEncoderWithPrinter[F[_]: Applicative](printer: Printer): EntityEncoder[F, Json] =
    EntityEncoder[F, Chunk[Byte]]
      .contramap[Json] { json =>
        val bytes = printer.prettyByteBuffer(json)
        Chunk.byteBuffer(bytes)
      }
      .withContentType(`Content-Type`(MediaType.application.json))

  def jsonEncoderOf[F[_]: Applicative, A](implicit encoder: Encoder[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinterOf(defaultPrinter)

  def jsonEncoderWithPrinterOf[F[_]: Applicative, A](printer: Printer)(
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
sealed abstract case class CirceInstancesBuilder private[circe] (
    defaultPrinter: Printer = Printer.noSpaces,
    jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
      CirceInstances.defaultJsonDecodeError,
    circeParseExceptionMessage: ParsingFailure => DecodeFailure =
      CirceInstances.defaultCirceParseError,
    jawnParseExceptionMessage: ParseException => DecodeFailure =
      JawnInstances.defaultJawnParseExceptionMessage,
    jawnEmptyBodyMessage: DecodeFailure = JawnInstances.defaultJawnEmptyBodyMessage
) { self =>
  def withPrinter(pp: Printer): CirceInstancesBuilder =
    this.copy(defaultPrinter = pp)

  def withJsonDecodeError(
      f: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure): CirceInstancesBuilder =
    this.copy(jsonDecodeError = f)

  def withJawnParseExceptionMessage(f: ParseException => DecodeFailure): CirceInstancesBuilder =
    this.copy(jawnParseExceptionMessage = f)
  def withCirceParseExceptionMessage(f: ParsingFailure => DecodeFailure): CirceInstancesBuilder =
    this.copy(circeParseExceptionMessage = f)

  def withEmptyBodyMessage(df: DecodeFailure): CirceInstancesBuilder =
    this.copy(jawnEmptyBodyMessage = df)

  protected def copy(
      defaultPrinter: Printer = self.defaultPrinter,
      jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure = self.jsonDecodeError,
      circeParseExceptionMessage: ParsingFailure => DecodeFailure = self.circeParseExceptionMessage,
      jawnParseExceptionMessage: ParseException => DecodeFailure = self.jawnParseExceptionMessage,
      jawnEmptyBodyMessage: DecodeFailure = self.jawnEmptyBodyMessage
  ): CirceInstancesBuilder =
    new CirceInstancesBuilder(
      defaultPrinter,
      jsonDecodeError,
      circeParseExceptionMessage,
      jawnParseExceptionMessage,
      jawnEmptyBodyMessage) {}

  def build: CirceInstances = new CirceInstances {
    override val defaultPrinter: Printer = self.defaultPrinter
    override val jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
      self.jsonDecodeError
    override val circeParseExceptionMessage: ParsingFailure => DecodeFailure =
      self.circeParseExceptionMessage
    override val jawnParseExceptionMessage: ParseException => DecodeFailure =
      self.jawnParseExceptionMessage
    override val jawnEmptyBodyMessage: DecodeFailure = self.jawnEmptyBodyMessage
  }
}

object CirceInstances {
  def withPrinter(p: Printer): CirceInstancesBuilder =
    builder.withPrinter(p)

  val builder: CirceInstancesBuilder = new CirceInstancesBuilder() {}

  // These are lazy since they are used when initializing the `builder`!

  private[circe] lazy val defaultCirceParseError: ParsingFailure => DecodeFailure =
    pe => MalformedMessageBodyFailure("Invalid JSON", Some(pe))

  private[circe] lazy val defaultJsonDecodeError
    : (Json, NonEmptyList[DecodingFailure]) => DecodeFailure = { (json, failures) =>
    InvalidMessageBodyFailure(
      s"Could not decode JSON: $json",
      if (failures.tail.isEmpty) Some(failures.head) else Some(DecodingFailures(failures)))
  }
}
