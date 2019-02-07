package org.http4s
package json4s

import cats._
import cats.effect._
import cats.syntax.all._
import org.http4s.headers.`Content-Type`
import org.json4s._
import org.json4s.JsonAST.JValue
import org.typelevel.jawn.support.json4s.Parser

object CustomParser extends Parser(useBigDecimalForDouble = true, useBigIntForLong = true)

trait Json4sInstances[J] {
  implicit def jsonDecoder[F[_]](implicit F: Sync[F]): EntityDecoder[F, JValue] =
    jawn.jawnDecoder(F, CustomParser.facade)

  def jsonOf[F[_], A](implicit reader: Reader[A], F: Sync[F]): EntityDecoder[F, A] =
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
  def jsonExtract[F[_], A](
      implicit F: Sync[F],
      formats: Formats,
      manifest: Manifest[A]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      DecodeResult(
        F.delay[Either[DecodeFailure, A]](Right(json.extract[A]))
          .handleError(e => Left(InvalidMessageBodyFailure("Could not extract JSON", Some(e)))))
    }

  protected def jsonMethods: JsonMethods[J]

  implicit def jsonEncoder[F[_], A <: JValue]: EntityEncoder[F, A] =
    EntityEncoder
      .stringEncoder(Charset.`UTF-8`)
      .contramap[A] { json =>
        // TODO naive implementation materializes to a String.
        // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
        jsonMethods.compact(jsonMethods.render(json))
      }
      .withContentType(`Content-Type`(MediaType.application.json))

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

  implicit class MessageSyntax[F[_]: Sync](self: Message[F]) {
    def decodeJson[A](implicit decoder: Reader[A]): F[A] =
      self.as(implicitly, jsonOf[F, A])
  }
}
