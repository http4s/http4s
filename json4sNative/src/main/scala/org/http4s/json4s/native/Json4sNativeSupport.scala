package org.http4s.json4s.native

import org.http4s.json4s.Json4sWritableInstances
import org.json4s.JsonAST.JValue
import org.json4s.native._

trait Json4sNativeSupport extends Json4sNativeWritableInstances

object Json4sNativeSupport extends Json4sNativeSupport

trait Json4sNativeWritableInstances extends Json4sWritableInstances {
  override protected def jValueToString(jValue: JValue): String = compactJson(renderJValue(jValue))
}
