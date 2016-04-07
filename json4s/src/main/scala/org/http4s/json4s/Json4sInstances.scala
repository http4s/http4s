package org.http4s
package json4s

import org.http4s.headers.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s._
import _root_.jawn.support.json4s.Parser.facade

import scala.util.control.NonFatal
import scalaz.{EitherT, \/}

trait Json4sInstances[J] {
  implicit lazy val jsonDecoder: EntityDecoder[JValue] = jawn.jawnDecoder(facade)

  def jsonOf[A](implicit reader: Reader[A]): EntityDecoder[A] =
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
  def jsonExtract[A](implicit formats: Formats, manifest: Manifest[A]): EntityDecoder[A] =
    jsonDecoder.flatMapR { json =>
      try DecodeResult.success(json.extract[A])
      catch {
        case NonFatal(e) => DecodeResult.failure(InvalidMessageBodyFailure("Could not extract JSON", Some(e)))
      }
    }

  protected def jsonMethods: JsonMethods[J]

  implicit def jsonEncoder[A <: JValue]: EntityEncoder[A] =
    EntityEncoder.stringEncoder(Charset.`UTF-8`).contramap[A] { json =>
      // TODO naive implementation materializes to a String.
      // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
      jsonMethods.compact(jsonMethods.render(json))
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[A](implicit writer: Writer[A]): EntityEncoder[A] =
    jsonEncoder.contramap[A](writer.write)
}
