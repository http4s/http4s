package org.http4s
package circe

import java.nio.ByteBuffer

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.either._
import fs2.{Chunk, Stream}
import io.circe._
import io.circe.jawn._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnInstances
import org.typelevel.jawn.ParseException

trait CirceInstances extends JawnInstances {
  private val circeSupportParser =
    new CirceSupportParser(maxValueSize = None, allowDuplicateKeys = false)
  import circeSupportParser.facade

  protected def defaultPrinter: Printer = Printer.noSpaces

  protected def circeParseExceptionMessage: ParsingFailure => DecodeFailure =
    CirceInstances.defaultCirceParseError

  protected def jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
    CirceInstances.defaultJsonDecodeError

  def jsonDecoderIncremental[F[_]: Sync]: EntityDecoder[F, Json] =
    this.jawnDecoder[F, Json]

  def jsonDecoderByteBuffer[F[_]: Sync]: EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(MediaType.application.json)(jsonDecoderByteBufferImpl[F])

  private def jsonDecoderByteBufferImpl[F[_]: Sync](m: Media[F]): DecodeResult[F, Json] =
    EntityDecoder.collectBinary(m).subflatMap { chunk =>
      val bb = ByteBuffer.wrap(chunk.toArray)
      if (bb.hasRemaining)
        parseByteBuffer(bb).leftMap(circeParseExceptionMessage)
      else
        Left(jawnEmptyBodyMessage)
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

  def jsonOf[F[_]: Sync, A: Decoder]: EntityDecoder[F, A] =
    jsonOfWithMedia(MediaType.application.json)

  def jsonOfWithMedia[F[_], A](r1: MediaRange, rs: MediaRange*)(
      implicit F: Sync[F],
      decoder: Decoder[A]): EntityDecoder[F, A] =
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
  def accumulatingJsonOf[F[_], A](implicit F: Sync[F], decoder: Decoder[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      decoder
        .decodeAccumulating(json.hcursor)
        .fold(
          failures => DecodeResult.failure(jsonDecodeError(json, failures)),
          DecodeResult.success(_)
        )
    }

  implicit def jsonEncoder[F[_]]: EntityEncoder[F, Json] =
    jsonEncoderWithPrinter(defaultPrinter)

  private def fromJsonToChunk(printer: Printer)(json: Json): Chunk[Byte] =
    Chunk.byteBuffer(printer.printToByteBuffer(json))

  def jsonEncoderWithPrinter[F[_]](printer: Printer): EntityEncoder[F, Json] =
    EntityEncoder[F, Chunk[Byte]]
      .contramap[Json](fromJsonToChunk(printer))
      .withContentType(`Content-Type`(MediaType.application.json))

  def jsonEncoderOf[F[_], A: Encoder]: EntityEncoder[F, A] =
    jsonEncoderWithPrinterOf(defaultPrinter)

  def jsonEncoderWithPrinterOf[F[_], A](printer: Printer)(
      implicit encoder: Encoder[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinter[F](printer).contramap[A](encoder.apply)

  implicit def streamJsonArrayEncoder[F[_]]: EntityEncoder[F, Stream[F, Json]] =
    streamJsonArrayEncoderWithPrinter(defaultPrinter)

  /** An [[EntityEncoder]] for a [[Stream]] of JSONs, which will encode it as a single JSON array. */
  def streamJsonArrayEncoderWithPrinter[F[_]](printer: Printer): EntityEncoder[F, Stream[F, Json]] =
    EntityEncoder
      .streamEncoder[F, Chunk[Byte]]
      .contramap[Stream[F, Json]] { stream =>
        val jsons = stream.map(fromJsonToChunk(printer))
        CirceInstances.openBrace ++ jsons.intersperse(CirceInstances.comma) ++ CirceInstances.closeBrace
      }
      .withContentType(`Content-Type`(MediaType.application.json))

  def streamJsonArrayEncoderOf[F[_], A: Encoder]: EntityEncoder[F, Stream[F, A]] =
    streamJsonArrayEncoderWithPrinterOf(defaultPrinter)

  /** An [[EntityEncoder]] for a [[Stream]] of values, which will encode it as a single JSON array. */
  def streamJsonArrayEncoderWithPrinterOf[F[_], A](printer: Printer)(
      implicit encoder: Encoder[A]): EntityEncoder[F, Stream[F, A]] =
    streamJsonArrayEncoderWithPrinter[F](printer).contramap[Stream[F, A]](_.map(encoder.apply))

  implicit val encodeUri: Encoder[Uri] =
    Encoder.encodeString.contramap[Uri](_.toString)

  implicit val decodeUri: Decoder[Uri] =
    Decoder.decodeString.emap { str =>
      Uri.fromString(str).leftMap(_ => "Uri")
    }

  implicit final def toMessageSynax[F[_]](req: Message[F]): CirceInstances.MessageSyntax[F] =
    new CirceInstances.MessageSyntax(req)
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

  // Constant byte chunks for the stream as JSON array encoder.

  private final val openBrace: Stream[fs2.Pure, Chunk[Byte]] =
    Stream.emit(Chunk.singleton('['.toByte))

  private final val closeBrace: Stream[fs2.Pure, Chunk[Byte]] =
    Stream.emit(Chunk.singleton(']'.toByte))

  private final val comma: Chunk[Byte] =
    Chunk.singleton(','.toByte)

  // Extension methods.

  private[circe] final class MessageSyntax[F[_]](private val req: Message[F]) extends AnyVal {
    def asJson(implicit F: JsonDecoder[F]): F[Json] =
      F.asJson(req)

    def asJsonDecode[A](implicit F: JsonDecoder[F], decoder: Decoder[A]): F[A] =
      F.asJsonDecode(req)

    def decodeJson[A](implicit F: Sync[F], decoder: Decoder[A]): F[A] =
      req.as(F, jsonOf[F, A])

    def json(implicit F: Sync[F]): F[Json] =
      req.as(F, jsonDecoder[F])
  }
}
