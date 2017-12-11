package org.http4s
package json4s

import cats._
import cats.effect._
import cats.syntax.all._
import _root_.jawn.support.json4s.Parser
import org.http4s.headers.`Content-Type`
import org.json4s._
import org.json4s.JsonAST.JValue
import scala.util.control.NonFatal

object CustomParser extends Parser(useBigDecimalForDouble = true, useBigIntForLong = true)

trait Json4sInstances[J] {
  import CustomParser.facade

  implicit def jsonDecoder[F[_]: Effect]: EntityDecoder[F, JValue] =
    jawn.jawnDecoder

  def jsonOf[F[_], A](implicit reader: Reader[A], F: Effect[F]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      DecodeResult(
        F.delay(reader.read(json))
          .map[Either[DecodeFailure, A]](Right(_))
          .recover {
            case e: MappingException =>
              Left(InvalidMessageBodyFailure("Could not map JSON", Some(e)))
          })
    }

  /**
    * Uses formats to extract a value from JSON.
    *
    * Editorial: This is heavily dependent on reflection. This is more idiomatic json4s, but less
    * idiomatic http4s, than [[jsonOf]].
    */
  def jsonExtract[F[_]: Effect, A](
      implicit formats: Formats,
      manifest: Manifest[A]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      try DecodeResult.success(json.extract[A])
      catch {
        case NonFatal(e) =>
          DecodeResult.failure(InvalidMessageBodyFailure("Could not extract JSON", Some(e)))
      }
    }

  protected def jsonMethods: JsonMethods[J]

  implicit def jsonEncoder[F[_], A <: JValue](implicit F: Applicative[F]): EntityEncoder[F, A] =
    EntityEncoder
      .stringEncoder(F, Charset.`UTF-8`)
      .contramap[A] { json =>
        // TODO naive implementation materializes to a String.
        // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
        jsonMethods.compact(jsonMethods.render(json))
      }
      .withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[F[_]: Applicative, A](implicit writer: Writer[A]): EntityEncoder[F, A] =
    jsonEncoder[F, JValue].contramap[A](writer.write)

  implicit val uriWriter: JsonFormat[Uri] =
    new JsonFormat[Uri] {
      def read(json: JValue): Uri =
        json match {
          case JString(s) =>
            Uri
              .fromString(s)
              .fold(
                _ => throw new MappingException(s"Can't convert $json to Uri."),
                identity
              )
          case _ =>
            throw new MappingException(s"Can't convert $json to Uri.")
        }

      def write(uri: Uri): JValue =
        JString(uri.toString)
    }

  implicit class MessageSyntax[F[_]: Effect](self: Message[F]) {
    def decodeJson[A](implicit decoder: Reader[A]): F[A] =
      self.as(implicitly, jsonOf[F, A])
  }
}
