package org.http4s
package json4s

import org.http4s.Header.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s.{MappingException, Writer, Reader, JsonMethods}
import _root_.jawn.support.json4s.Parser.facade

trait Json4sInstances[J] {
  implicit lazy val json: EntityDecoder[JValue] = jawn.jawnDecoder(facade)

  def jsonOf[A](implicit reader: Reader[A]): EntityDecoder[A] =
    json.flatMapR { json =>
      try DecodeResult.success(reader.read(json))
      catch {
        case e: MappingException => DecodeResult.failure(ParseFailure("Could not map JSON", e.msg))
      }
    }

  protected def jsonMethods: JsonMethods[J]

  implicit lazy val jsonEncoder: EntityEncoder[JValue] =
    EntityEncoder[String].contramap[JValue] { json =>
      // TODO naive implementation materializes to a String.
      // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
      jsonMethods.compact(jsonMethods.render(json))
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  def jsonEncoderOf[A](implicit writer: Writer[A]): EntityEncoder[A] =
    jsonEncoder.contramap[A](writer.write)
}
