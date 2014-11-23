package org.http4s
package json4s
package jackson

import org.json4s.JsonAST.JValue

trait Json4sJacksonSupport extends Json4sSupport[JValue] {
  override protected def jsonMethods = org.json4s.jackson.JsonMethods
}

object Json4sJacksonSupport extends Json4sJacksonSupport
