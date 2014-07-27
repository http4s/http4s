package org.http4s.json4s.jackson

import org.http4s.json4s.Json4sWritableInstances
import org.json4s.JsonAST.JValue
import org.json4s.jackson._

trait Json4sJacksonSupport extends Json4sJacksonWritableInstances

object Json4sJacksonSupport extends Json4sJacksonSupport

trait Json4sJacksonWritableInstances extends Json4sWritableInstances {
  override implicit protected def jValueToString(jValue: JValue): String = compactJson(renderJValue(jValue))
}
