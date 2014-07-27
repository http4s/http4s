package org.http4s.json4s
package native

import org.http4s.{CharacterSet, Writable}
import org.json4s.JsonAST.JValue
import org.json4s.native._

trait Json4sNativeWritableInstances extends Json4sWritableInstances {
  override protected def jValueToString(jValue: JValue): String = compactJson(renderJValue(jValue))
}
