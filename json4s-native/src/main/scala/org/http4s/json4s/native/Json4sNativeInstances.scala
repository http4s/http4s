package org.http4s
package json4s
package native

import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods
import _root_.jawn.support.json4s.Parser.facade

trait Json4sNativeInstances {
  implicit val json: EntityDecoder[JValue] = jawn.jawnDecoder(facade)

  implicit val jsonEncoder: EntityEncoder[JValue] = json4sEncode(JsonMethods)
}
