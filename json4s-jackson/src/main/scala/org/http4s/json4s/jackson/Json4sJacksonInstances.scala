package org.http4s
package json4s
package jackson

import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods
import _root_.jawn.support.json4s.Parser.facade

trait Json4sJacksonInstances extends Json4sInstances {
  implicit val json: EntityDecoder[JValue] = jawn.jawnDecoder(facade)

  implicit val jsonEncoder: EntityEncoder[JValue] = json4sEncode(JsonMethods)
}
