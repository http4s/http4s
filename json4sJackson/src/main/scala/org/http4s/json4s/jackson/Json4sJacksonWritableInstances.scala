package org.http4s.json4s
package jackson

import org.json4s.JsonAST.JValue
import org.json4s.jackson._

trait Json4sJacksonWritableInstances extends Json4sWritableInstances {
  override implicit protected def jValueToString(jValue: JValue): String = compactJson(renderJValue(jValue))
}
