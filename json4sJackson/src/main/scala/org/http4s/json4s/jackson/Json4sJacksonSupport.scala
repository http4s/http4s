package org.http4s
package json4s
package jackson

import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods

trait Json4sJacksonSupport extends Json4sJacksonWritableInstances

object Json4sJacksonSupport extends Json4sJacksonSupport

trait Json4sJacksonWritableInstances extends Json4sWritableInstances[JValue] {
  implicit override protected final def jsonMethods = JsonMethods
}
