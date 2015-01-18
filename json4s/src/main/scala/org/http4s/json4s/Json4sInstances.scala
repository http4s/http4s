package org.http4s
package json4s

import org.http4s.Header.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s.JsonMethods
import _root_.jawn.support.json4s.Parser.facade

trait Json4sInstances {
  implicit def json: EntityDecoder[JValue] = jawn.jawnDecoder(facade)

  implicit def jsonEncoder[J](implicit jsonMethods: JsonMethods[J]): EntityEncoder[JValue] =
    EntityEncoder[String].contramap[JValue] { json =>
      // TODO naive implementation materializes to a String.
      // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
      jsonMethods.compact(jsonMethods.render(json))
    }.withContentType(`Content-Type`(MediaType.`application/json`))
}
