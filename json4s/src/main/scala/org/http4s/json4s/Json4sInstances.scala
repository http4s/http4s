package org.http4s
package json4s

import _root_.jawn.support.json4s.Parser.facade
import cats.{Applicative, MonadError}
import org.http4s.headers.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s._

import scala.util.control.NonFatal

trait Json4sInstances[J] {
  implicit def jsonDecoder[F[_]: MonadError[?[_], Throwable]]: EntityDecoder[F, JValue] =
    jawn.jawnDecoder

  def jsonOf[F[_]: MonadError[?[_], Throwable], A](implicit reader: Reader[A]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      try DecodeResult.success(reader.read(json))
      catch {
        case e: MappingException => DecodeResult.failure(InvalidMessageBodyFailure("Could not map JSON", Some(e)))
      }
    }

  /**
    * Uses formats to extract a value from JSON.
    *
    * Editorial: This is heavily dependent on reflection. This is more idiomatic json4s, but less
    * idiomatic http4s, than [[jsonOf]].
    */
  def jsonExtract[F[_]: MonadError[?[_], Throwable], A](implicit formats: Formats, manifest: Manifest[A]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      try DecodeResult.success(json.extract[A])
      catch {
        case NonFatal(e) => DecodeResult.failure(InvalidMessageBodyFailure("Could not extract JSON", Some(e)))
      }
    }

  protected def jsonMethods: JsonMethods[J]

  implicit def jsonEncoder[F[_], A <: JValue](implicit F: Applicative[F]): EntityEncoder[F, A] =
    EntityEncoder.stringEncoder(F, Charset.`UTF-8`).contramap[A] { json =>
      // TODO naive implementation materializes to a String.
      // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
      jsonMethods.compact(jsonMethods.render(json))
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[F[_]: Applicative, A](implicit writer: Writer[A]): EntityEncoder[F, A] =
    jsonEncoder.contramap[A](writer.write)


  implicit val uriWriter: JsonFormat[Uri] =
    new JsonFormat[Uri] {
      def read(json: JValue): Uri =
        json match {
          case JString(s) =>
            Uri.fromString(s).fold(
              _ => throw new MappingException(s"Can't convert $json to Uri."),
              identity
            )
          case _ =>
            throw new MappingException(s"Can't convert $json to Uri.")
        }

      def write(uri: Uri): JValue =
        JString(uri.toString)
    }
}
