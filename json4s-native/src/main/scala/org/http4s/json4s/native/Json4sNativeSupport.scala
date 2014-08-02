package org.http4s
package json4s
package native

import org.json4s.native._
import scala.text.Document

trait Json4sNativeSupport extends Json4sNativeWritableInstances

object Json4sNativeSupport extends Json4sNativeSupport

trait Json4sNativeWritableInstances extends Json4sWritableInstances[Document] {
  implicit override protected final def jsonMethods = JsonMethods
}
